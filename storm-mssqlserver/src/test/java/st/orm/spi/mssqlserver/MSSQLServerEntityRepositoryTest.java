package st.orm.spi.mssqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.template.SqlInterceptor.observe;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Version;
import st.orm.core.template.PreparedStatementTemplate;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@Testcontainers
@StormTest(scripts = "/data.sql")
public class MSSQLServerEntityRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    public static MSSQLServerContainer<?> sqlServerContainer =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-latest")
                    .acceptLicense() // Accepts the license agreement required by MS SQL Server images
                    .withPassword("test@1234") // SQL Server requires a strong SA password
                    .waitingFor(Wait.forListeningPort());

    public static DataSource dataSource() {
        return ContainerDataSource.of(sqlServerContainer.getJdbcUrl(), sqlServerContainer.getUsername(),
                sqlServerContainer.getPassword());
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

    @BeforeEach
    void setUpBranchTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            executeSafe(statement, "DROP TABLE IF EXISTS version_long_entity");
            statement.execute("""
                    CREATE TABLE version_long_entity (
                        id int IDENTITY(1,1) PRIMARY KEY,
                        name varchar(255),
                        version bigint DEFAULT 0
                    )""");
            statement.execute("INSERT INTO version_long_entity (name) VALUES ('Alice')");
            statement.execute("INSERT INTO version_long_entity (name) VALUES ('Bob')");

            executeSafe(statement, "DROP TABLE IF EXISTS version_instant_entity");
            statement.execute("""
                    CREATE TABLE version_instant_entity (
                        id int IDENTITY(1,1) PRIMARY KEY,
                        name varchar(255),
                        version datetime2 DEFAULT CURRENT_TIMESTAMP
                    )""");
            statement.execute("INSERT INTO version_instant_entity (name) VALUES ('Alice')");
            statement.execute("INSERT INTO version_instant_entity (name) VALUES ('Bob')");

            executeSafe(statement, "DROP TABLE IF EXISTS non_autogen_entity");
            statement.execute("""
                    CREATE TABLE non_autogen_entity (
                        id int PRIMARY KEY,
                        name varchar(255),
                        version int DEFAULT 0
                    )""");
            statement.execute("INSERT INTO non_autogen_entity (id, name) VALUES (1, 'First')");
            statement.execute("INSERT INTO non_autogen_entity (id, name) VALUES (2, 'Second')");

            executeSafe(statement, "DROP TABLE IF EXISTS seq_named_entity");
            executeSafe(statement, "DROP SEQUENCE IF EXISTS seq_named_entity_id_seq");
            statement.execute("CREATE SEQUENCE seq_named_entity_id_seq START WITH 1 INCREMENT BY 1");
            statement.execute("""
                    CREATE TABLE seq_named_entity (
                        id int PRIMARY KEY DEFAULT (NEXT VALUE FOR seq_named_entity_id_seq),
                        name varchar(255)
                    )""");
            statement.execute(
                    "INSERT INTO seq_named_entity (id, name) VALUES (NEXT VALUE FOR seq_named_entity_id_seq, 'Alpha')");

            executeSafe(statement, "DROP TABLE IF EXISTS seq_empty_entity");
            statement.execute("""
                    CREATE TABLE seq_empty_entity (
                        id int IDENTITY(1,1) PRIMARY KEY,
                        name varchar(255)
                    )""");
            statement.execute("INSERT INTO seq_empty_entity (name) VALUES ('Alpha')");
            statement.execute("INSERT INTO seq_empty_entity (name) VALUES ('Beta')");
        }
    }

    private void executeSafe(java.sql.Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException ignore) {
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
    @DbTable("seq_named_entity")
    public record SeqNamedEntity(
            @PK(generation = SEQUENCE, sequence = "seq_named_entity_id_seq") Integer id,
            String name
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("seq_empty_entity")
    public record SeqEmptyEntity(
            @PK(generation = SEQUENCE) Integer id,
            String name
    ) implements Entity<Integer> {}

    @Test
    public void testUpsertAndFetchIdsWithNamedSequenceThrows() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqNamedEntity.class);
        assertThrows(PersistenceException.class, () ->
                repo.upsertAndFetchIds(List.of(SeqNamedEntity.builder().id(1).name("test").build())));
    }

    @Test
    public void testInsertAndFetchIdWithSequence() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEmptyEntity.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertTrue(sql.statement().contains("OUTPUT INSERTED"));
            }
        }, () -> {
            var id = repo.insertAndFetchId(SeqEmptyEntity.builder().name("Gamma").build());
            assertNotNull(id);
            assertTrue(id > 0);
        });
    }

    @Test
    public void testInsertAndFetchIdsWithSequence() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEmptyEntity.class);
        var ids = repo.insertAndFetchIds(List.of(
                SeqEmptyEntity.builder().name("Delta").build(),
                SeqEmptyEntity.builder().name("Epsilon").build()));
        assertEquals(2, ids.size());
        assertTrue(ids.get(0) > 0);
        assertTrue(ids.get(1) > 0);
    }

    @Test
    public void testUpsertNonAutoGenMerge() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(NonAutoGenEntity.class);

        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertTrue(sql.statement().contains("MERGE INTO"));
                assertTrue(sql.versionAware());
            }
        }, () -> {
            repo.upsert(NonAutoGenEntity.builder().id(1).name("First Updated").version(0).build());
            var updated = repo.getById(1);
            assertEquals("First Updated", updated.name());
            assertEquals(1, updated.version());
        });
    }

    @Test
    public void testUpsertNonAutoGenMergeInsert() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(NonAutoGenEntity.class);
        repo.upsert(NonAutoGenEntity.builder().id(3).name("Third").version(0).build());
        var created = repo.getById(3);
        assertEquals("Third", created.name());
    }

    @Test
    public void testUpsertNewEntityRoutesToInsert() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VersionLongEntity.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertTrue(sql.statement().contains("INSERT INTO"));
            }
        }, () -> {
            repo.upsert(VersionLongEntity.builder().name("New Entity").version(0L).build());
        });
        var entities = repo.findAll();
        assertTrue(entities.stream().anyMatch(entity -> "New Entity".equals(entity.name())));
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

    // Entity callbacks on the dialect-specific insert and upsert paths.

    @Test
    public void testSequenceInsertAndFetchIdFiresCallbacksWithGeneratedKey() {
        var observed = new java.util.ArrayList<SeqNamedEntity>();
        var orm = PreparedStatementTemplate.ORM(dataSource).withEntityCallback(new st.orm.EntityCallback<SeqNamedEntity>() {
            @Override
            public SeqNamedEntity beforeInsert(SeqNamedEntity entity) {
                return entity.toBuilder().name(entity.name().toUpperCase()).build();
            }

            @Override
            public void afterInsert(SeqNamedEntity entity) {
                observed.add(entity);
            }
        });
        var repo = orm.entity(SeqNamedEntity.class);
        // The OUTPUT INSERTED path for sequence keys is dialect-specific; it must still run the callbacks.
        var id = repo.insertAndFetchId(SeqNamedEntity.builder().name("callback seq").build());
        assertEquals("CALLBACK SEQ", repo.getById(id).name());
        assertEquals(1, observed.size());
        assertEquals(id, observed.getFirst().id());
    }

    @Test
    public void testBatchInsertAndFetchIdsFiresCallbacksWithGeneratedKeys() {
        var observed = new java.util.ArrayList<Vet>();
        var orm = PreparedStatementTemplate.ORM(dataSource).withEntityCallback(new st.orm.EntityCallback<Vet>() {
            @Override
            public Vet beforeInsert(Vet entity) {
                return entity.toBuilder().lastName(entity.lastName().toUpperCase()).build();
            }

            @Override
            public void afterInsert(Vet entity) {
                observed.add(entity);
            }
        });
        var repo = orm.entity(Vet.class);
        // Identity keys also take the dialect-specific batch path, because this dialect cannot return generated keys
        // from a JDBC batch.
        var ids = repo.insertAndFetchIds(List.of(
                Vet.builder().firstName("Cb").lastName("one").build(),
                Vet.builder().firstName("Cb").lastName("two").build()));
        assertEquals(2, ids.size());
        assertEquals("ONE", repo.getById(ids.get(0)).lastName());
        assertEquals("TWO", repo.getById(ids.get(1)).lastName());
        assertEquals(ids, observed.stream().map(Vet::id).toList());
        assertEquals(List.of("ONE", "TWO"), observed.stream().map(Vet::lastName).toList());
    }

    @Test
    public void testUpsertAndFetchIdsReportsGeneratedKeysToCallbacks() {
        var observed = new java.util.ArrayList<Vet>();
        var orm = PreparedStatementTemplate.ORM(dataSource).withEntityCallback(new st.orm.EntityCallback<Vet>() {
            @Override
            public void afterInsert(Vet entity) {
                observed.add(entity);
            }
        });
        // An auto-generated key routes the upsert to insert on this dialect, so the insert callbacks fire and must
        // carry the keys the database assigned.
        var ids = orm.entity(Vet.class).upsertAndFetchIds(List.of(
                Vet.builder().firstName("Upsert").lastName("cbOne").build(),
                Vet.builder().firstName("Upsert").lastName("cbTwo").build()));
        assertEquals(2, ids.size());
        assertEquals(ids, observed.stream().map(Vet::id).toList());
    }

    @Test
    public void testUpsertAndFetchIdsWithIdentityKeyRoutesToInsert() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
        // An auto-generated key cannot go through MERGE on this dialect, so the upsert routes to insert. The insert
        // has to run through the dialect's own path, because this driver cannot report generated keys for a JDBC
        // batch.
        var ids = repo.upsertAndFetchIds(List.of(
                Vet.builder().firstName("Routed").lastName("one").build(),
                Vet.builder().firstName("Routed").lastName("two").build()));
        assertEquals(2, ids.size());
        assertEquals("one", repo.getById(ids.get(0)).lastName());
        assertEquals("two", repo.getById(ids.get(1)).lastName());
    }
}
