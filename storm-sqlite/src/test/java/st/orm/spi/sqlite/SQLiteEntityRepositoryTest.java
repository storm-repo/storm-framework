package st.orm.spi.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.core.template.SqlInterceptor.observe;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Version;
import st.orm.core.template.PreparedStatementTemplate;
import st.orm.test.StormTest;

@StormTest(url = "jdbc:sqlite:target/SQLiteEntityRepositoryTest.db", scripts = "/data.sql")
public class SQLiteEntityRepositoryTest {

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

    @Test
    public void testUpsertInlineVersion() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var entity = repo.getById(1);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertTrue(sql.versionAware());
                assertEquals("Betty", sql.parameters().get(0).dbValue());
                assertEquals("Smith", sql.parameters().get(1).dbValue());
                assertEquals("638 Cardinal Ave.", sql.parameters().get(2).dbValue());
                assertEquals("Sun Prairie", sql.parameters().get(3).dbValue());
                assertEquals("6085551749", sql.parameters().get(4).dbValue());
                assertEquals(1, sql.parameters().get(5).dbValue());
                assertEquals(0, sql.parameters().get(6).dbValue());
            }
        }, () -> {
            repo.upsert(entity.toBuilder().lastName("Smith").build());
            var update = repo.getById(1);
            assertEquals("Betty", update.firstName());
            assertEquals("Smith", update.lastName());
            assertEquals("638 Cardinal Ave.", update.address().address());
            assertEquals("Sun Prairie", update.address().city());
            assertEquals("6085551749", update.telephone());
            assertEquals(1, update.version());
        });
    }

    @Test
    public void testUpsertAndFetchInlineVersionInsert() {
        String expectedSql = """
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, address = EXCLUDED.address, city = EXCLUDED.city, telephone = EXCLUDED.telephone, version = owner.version + 1""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var entity = repo.getById(1);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of("id"));
                assertTrue(sql.versionAware());
                assertEquals("Betty", sql.parameters().get(0).dbValue());
                assertEquals("Smith", sql.parameters().get(1).dbValue());
                assertEquals("638 Cardinal Ave.", sql.parameters().get(2).dbValue());
                assertEquals("Sun Prairie", sql.parameters().get(3).dbValue());
                assertEquals("6085551749", sql.parameters().get(4).dbValue());
            }
        }, () -> {
            var insert = repo.upsertAndFetch(entity.toBuilder()
                    .id(0)  // Default value.
                    .lastName("Smith").build());
            assertTrue(insert.id() != 1);
            assertEquals("Betty", insert.firstName());
            assertEquals("Smith", insert.lastName());
            assertEquals("638 Cardinal Ave.", insert.address().address());
            assertEquals("Sun Prairie", insert.address().city());
            assertEquals("6085551749", insert.telephone());
            assertEquals(0, insert.version());
        });
    }

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

    @Test
    public void testUpsertNonAutoGenerated() {
        String expectedSql = """
                INSERT INTO specialty (id, name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class);
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertEquals(sql.generatedKeys(), List.of());
            assertFalse(sql.versionAware());
            assertEquals(4, sql.parameters().get(0).dbValue());
            assertEquals("anaesthetics", sql.parameters().get(1).dbValue());
        }, () -> repo.upsert(Specialty.builder().id(4).name("anaesthetics").build()));
        var entity = repo.select().where(Metamodel.of(Specialty.class, "name"), EQUALS, "anaesthetics").getSingleResult();
        repo.upsert(entity.toBuilder().name("anaesthetist").build());
        var updated = repo.select().where(Metamodel.of(Specialty.class, "name"), EQUALS, "anaesthetist").getSingleResult();
        assertEquals(entity.id(), updated.id());
        assertEquals("anaesthetist", updated.name());
    }

    @Test
    public void testUpsertAndFetchNonAutoGenerated() {
        String expectedSql = """
                INSERT INTO specialty (id, name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertEquals(4, sql.parameters().get(0).dbValue());
                assertEquals("anaesthetics", sql.parameters().get(1).dbValue());
            }
        }, () -> {
            var entity = repo.upsertAndFetch(Specialty.builder().id(4).name("anaesthetics").build());
            var updated = repo.upsertAndFetch(entity.toBuilder().name("anaesthetist").build());
            assertEquals(entity.id(), updated.id());
            assertEquals("anaesthetist", updated.name());
        });
    }

    @Test
    public void testUpsertNonAutoGeneratedBatch() {
        String expectedSql = """
                INSERT INTO specialty (id, name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class);
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertEquals(sql.generatedKeys(), List.of());
            assertFalse(sql.versionAware());
            assertTrue(sql.bindVariables().isPresent());
        }, () -> repo.upsert(List.of(
                Specialty.builder().id(4).name("anaesthetics").build(),
                Specialty.builder().id(5).name("nurse").build())));
        var entities = repo.select().where(Metamodel.of(Specialty.class, "id"), GREATER_THAN_OR_EQUAL, 4).getResultList();
        repo.upsert(entities.stream().map(e -> e.toBuilder().name("%ss".formatted(e.name())).build()).toList());
        var updated = repo.select().where(Metamodel.of(Specialty.class, "id"), GREATER_THAN_OR_EQUAL, 4).getResultList();
        assertEquals(2, updated.size());
        assertTrue(updated.stream().allMatch(entity -> entity.name().endsWith("s")));
    }

    @Test
    public void testUpsertAndFetchNonAutoGeneratedBatch() {
        String expectedSql = """
                INSERT INTO specialty (id, name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var entities = repo.upsertAndFetch(List.of(
                    Specialty.builder().id(4).name("anaesthetics").build(),
                    Specialty.builder().id(5).name("nurse").build()));
            var updated = repo.upsertAndFetch(entities.stream().map(e -> e.toBuilder().name("%ss".formatted(e.name())).build()).toList());
            assertEquals(2, updated.size());
            assertTrue(updated.stream().allMatch(entity -> entity.name().endsWith("s")));
        });
    }

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

    @BeforeEach
    void setUpBranchTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                    DROP TABLE IF EXISTS version_long_entity;
                    """);
            statement.execute("""
                    CREATE TABLE version_long_entity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255),
                        version INTEGER DEFAULT 0
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
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255),
                        version TIMESTAMP DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now'))
                    );
                    """);
            statement.execute("""
                    INSERT INTO version_instant_entity (name) VALUES ('Alice');
                    """);
            statement.execute("""
                    INSERT INTO version_instant_entity (name) VALUES ('Bob');
                    """);
            statement.execute("""
                    DROP TABLE IF EXISTS pk_only_entity;
                    """);
            statement.execute("""
                    CREATE TABLE pk_only_entity (
                        id INTEGER PRIMARY KEY
                    );
                    """);
            statement.execute("""
                    INSERT INTO pk_only_entity (id) VALUES (1);
                    """);
            statement.execute("""
                    INSERT INTO pk_only_entity (id) VALUES (2);
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
    @DbTable("pk_only_entity")
    public record PkOnlyEntity(
            @PK(generation = NONE) Integer id
    ) implements Entity<Integer> {}

    @Test
    public void testUpsertBatchWithVersionInstant() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VersionInstantEntity.class);
        var entity1 = repo.getById(1);
        var entity2 = repo.getById(2);

        repo.upsert(List.of(
                entity1.toBuilder().name("Alice Instant Batch").build(),
                entity2.toBuilder().name("Bob Instant Batch").build()));

        var updated1 = repo.getById(1);
        var updated2 = repo.getById(2);
        assertEquals("Alice Instant Batch", updated1.name());
        assertEquals("Bob Instant Batch", updated2.name());
    }

    @Test
    public void testUpsertPkOnlyEntity() {
        String expectedSql = """
                INSERT INTO pk_only_entity (id)
                VALUES (?)
                ON CONFLICT (id) DO NOTHING""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PkOnlyEntity.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
            }
        }, () -> {
            repo.upsert(PkOnlyEntity.builder().id(1).build());
            repo.upsert(PkOnlyEntity.builder().id(3).build());
        });
        assertEquals(3, repo.findAll().size());
    }

    @Test
    public void testUpsertBatchPkOnlyEntity() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PkOnlyEntity.class);
        repo.upsert(List.of(
                PkOnlyEntity.builder().id(1).build(),
                PkOnlyEntity.builder().id(2).build(),
                PkOnlyEntity.builder().id(4).build()));
        assertEquals(3, repo.findAll().stream()
                .filter(entity -> entity.id() >= 3 || entity.id() <= 2)
                .count());
    }
}
