package st.orm.spi.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.Operator.EQUALS;
import static st.orm.core.template.SqlInterceptor.observe;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Version;
import st.orm.core.template.PreparedStatementTemplate;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@Testcontainers
@StormTest(scripts = "/data.sql")
public class MySQLEntityRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:9.2")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort());
    ;

    public static DataSource dataSource() {
        return ContainerDataSource.of(mysqlContainer.getJdbcUrl(), mysqlContainer.getUsername(),
                mysqlContainer.getPassword());
    }

    private DataSource dataSource;

    @BeforeEach
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Builder(toBuilder = true)
    public record Vet(
            @PK Integer id,
            String firstName,
            String lastName
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    public record Address(
            String address,
            String city
    ) {}

    @Builder(toBuilder = true)
    public record Owner(
            @PK Integer id,
            String firstName,
            String lastName,
            Address address,
            @Nullable String telephone,
            @Version int version
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    public record PetType(
            @PK Integer id,
            String name,
            @Nullable String description
    ) implements Entity<Integer> {}

    @Test
    public void testUpsertUniqueKey() {
        // Mysql is able to update a record with the same unique key, where Oracle throws an exception.
        String expectedSql = """
                INSERT INTO pet_type (name, description)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), description = VALUES(description)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetType.class);
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertEquals(sql.generatedKeys(), List.of("id"));
            assertFalse(sql.versionAware());
            assertEquals("dragon", sql.parameters().get(0).dbValue());
            assertEquals("description", sql.parameters().get(1).dbValue());
        }, () -> repo.upsert(PetType.builder().name("dragon").description("description").build()));
        var entity = repo.select().where(Metamodel.of(PetType.class, "name"), EQUALS, "dragon").getSingleResult();
        assertEquals("description", entity.description());
        repo.upsert(PetType.builder().name("dragon").description(null).build());
        var updated = repo.select().where(Metamodel.of(PetType.class, "name"), EQUALS, "dragon").getSingleResult();
        assertNull(updated.description(), "description");
    }

    @Builder(toBuilder = true)
    public record Specialty(
            @PK(generation = NONE) Integer id,
            String name
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    public record VetSpecialtyPK(
            int vetId,
            int specialtyId
    ) {}

    @Builder(toBuilder = true)
    public record VetSpecialty(
            @PK(generation = NONE) VetSpecialtyPK id,  // Implicitly @Inlined
            @Persist(insertable = false, updatable = false) @FK Vet vet,
            @Persist(insertable = false, updatable = false) @FK Specialty specialty) implements Entity<VetSpecialtyPK> {
        public VetSpecialty(VetSpecialtyPK pk) {
            //noinspection DataFlowIssue
            this(pk, null, null);
        }
    }

    @Builder(toBuilder = true)
    @DbTable("pet")
    public record Pet(
            @PK(generation = SEQUENCE, sequence = "pet_id_seq") Integer id,
            String name,
            LocalDate birthDate,
            @FK PetType type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("pet")
    public record PetSequenceEmpty(
            @PK(generation = SEQUENCE) Integer id,
            String name,
            LocalDate birthDate,
            @FK PetType type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Test
    public void testUpsertAndFetchWithSequenceExisting() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        // First insert a pet with explicit id using ignoreAutoGenerate.
        repo.insert(Pet.builder()
                .id(100)
                .name("Buddy")
                .birthDate(LocalDate.of(2020, 1, 1))
                .type(PetType.builder().id(1).build())
                .owner(Owner.builder().id(1).build())
                .build(), true);
        // Now upsert the same pet with an existing non-default id.
        var updated = repo.upsertAndFetch(Pet.builder().id(100).name("Max")
                .birthDate(LocalDate.of(2020, 1, 1))
                .type(PetType.builder().id(1).build())
                .owner(Owner.builder().id(1).build())
                .build());
        assertEquals(100, updated.id());
        assertEquals("Max", updated.name());
    }

    @Test
    public void testUpsertAndFetchWithSequenceExistingBatch() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var e = assertThrows(PersistenceException.class, () ->
            repo.upsertAndFetch(List.of(
                    Pet.builder().id(1).name("Max").birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build()).owner(Owner.builder().id(1).build()).build(),
                    Pet.builder().id(2).name("Bella").birthDate(LocalDate.of(2020, 2, 1))
                            .type(PetType.builder().id(1).build()).owner(Owner.builder().id(1).build()).build()
            )));
        assertNull(e.getCause(), "Exception must be raised by storm.");
    }

    @BeforeEach
    void setUpBranchTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS version_long_entity;
                    """);
            statement.execute("""
                    CREATE TABLE version_long_entity (
                        id integer AUTO_INCREMENT PRIMARY KEY,
                        name varchar(255),
                        version bigint DEFAULT 0
                    );
                    """);
            statement.execute("""
                    INSERT INTO version_long_entity (name) VALUES ('Alice');
                    """);
            statement.execute("""
                    INSERT INTO version_long_entity (name) VALUES ('Bob');
                    """);
            statement.execute("""
                    DROP TABLE IF EXISTS version_instant_entity;
                    """);
            statement.execute("""
                    CREATE TABLE version_instant_entity (
                        id integer AUTO_INCREMENT PRIMARY KEY,
                        name varchar(255),
                        version timestamp DEFAULT CURRENT_TIMESTAMP
                    );
                    """);
            statement.execute("""
                    INSERT INTO version_instant_entity (name) VALUES ('Alice');
                    """);
            statement.execute("""
                    INSERT INTO version_instant_entity (name) VALUES ('Bob');
                    """);
            statement.execute("""
                    DROP TABLE IF EXISTS non_autogen_entity;
                    """);
            statement.execute("""
                    CREATE TABLE non_autogen_entity (
                        id integer PRIMARY KEY,
                        name varchar(255),
                        version integer DEFAULT 0
                    );
                    """);
            statement.execute("""
                    INSERT INTO non_autogen_entity (id, name) VALUES (1, 'First');
                    """);
            statement.execute("""
                    INSERT INTO non_autogen_entity (id, name) VALUES (2, 'Second');
                    """);
            statement.execute("""
                    DROP TABLE IF EXISTS seq_entity;
                    """);
            statement.execute("""
                    CREATE TABLE seq_entity (
                        id integer PRIMARY KEY,
                        name varchar(255)
                    );
                    """);
        }
    }

    @Builder(toBuilder = true)
    @DbTable("version_long_entity")
    public record VersionLongEntity(
            @PK Integer id,
            String name,
            @Version long version
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("version_instant_entity")
    public record VersionInstantEntity(
            @PK Integer id,
            String name,
            @Version @Nullable Instant version
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("non_autogen_entity")
    public record NonAutoGenEntity(
            @PK(generation = NONE) Integer id,
            String name,
            @Version int version
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("seq_entity")
    public record SeqEntity(
            @PK(generation = SEQUENCE) Integer id,
            String name
    ) implements Entity<Integer> {}

    @Test
    public void testInsertAndFetchIdWithSequenceThrows() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        assertThrows(PersistenceException.class, () ->
                repo.insertAndFetchId(SeqEntity.builder().name("test").build()));
    }

    @Test
    public void testInsertAndFetchIdsWithSequenceThrows() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        assertThrows(PersistenceException.class, () ->
                repo.insertAndFetchIds(List.of(SeqEntity.builder().name("test").build())));
    }

    @Test
    public void testUpsertAndFetchIdsWithSequenceThrows() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        assertThrows(PersistenceException.class, () ->
                repo.upsertAndFetchIds(List.of(SeqEntity.builder().id(1).name("test").build())));
    }

    @Test
    public void testUpsertNonAutoGeneratedPk() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(NonAutoGenEntity.class);

        repo.upsert(NonAutoGenEntity.builder().id(1).name("First Updated").version(0).build());
        var updated = repo.getById(1);
        assertEquals("First Updated", updated.name());

        repo.upsert(NonAutoGenEntity.builder().id(3).name("Third").version(0).build());
        var created = repo.getById(3);
        assertEquals("Third", created.name());
    }

    @Test
    public void testUpsertNewEntityWithAutoIncrementPk() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VersionLongEntity.class);
        repo.upsert(VersionLongEntity.builder().name("New Entity").version(0L).build());
        var entities = repo.findAll();
        assertTrue(entities.stream().anyMatch(entity -> "New Entity".equals(entity.name())));
    }

    @Test
    public void testUpsertAndFetchIdNewEntityWithAutoIncrementPk() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VersionLongEntity.class);
        var id = repo.upsertAndFetchId(VersionLongEntity.builder().name("New Fetch").version(0L).build());
        assertNotNull(id);
        assertTrue(id > 0);
    }

    // UUID support

    @Builder(toBuilder = true)
    @DbTable("api_key")
    public record ApiKey(
            @PK(generation = NONE) UUID id,
            String name,
            @Nullable UUID externalReference
    ) implements Entity<UUID> {}

    private static final UUID DEFAULT_KEY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID SECONDARY_KEY_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private static final UUID DEFAULT_KEY_EXTERNAL_REF = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");

}
