package st.orm.spi.sqlite;

import static st.orm.GenerationStrategy.NONE;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Version;
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

}
