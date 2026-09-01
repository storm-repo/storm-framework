package st.orm.spi.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;

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
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Version;
import st.orm.core.template.PreparedStatementTemplate;
import st.orm.test.StormTest;

@StormTest(scripts = "/data.sql")
public class H2EntityRepositoryTest {

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
    public record Specialty(
            @PK(generation = NONE) Integer id,
            String name
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("specialty_note")
    public record SpecialtyNote(
            @PK(generation = NONE) @FK Specialty specialty,  // Dependent one-to-one: the PK is the FK.
            String note,
            Instant updatedAt
    ) implements Entity<Specialty> {}

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
    @DbTable("vet_specialty_note")
    public record VetSpecialtyNote(
            @PK(generation = NONE) @FK VetSpecialty vetSpecialty,  // The PK is a compound FK spanning two columns.
            String note
    ) implements Entity<VetSpecialty> {}

    @Builder(toBuilder = true)
    @DbTable("vet_specialty_note_audit")
    public record VetSpecialtyNoteAudit(
            @PK(generation = NONE) @FK VetSpecialtyNote note,  // The referenced key chain is two levels deep.
            String remark
    ) implements Entity<VetSpecialtyNote> {}

    @Builder(toBuilder = true)
    @DbTable("specialty_note_history")
    public record SpecialtyNoteHistory(
            @PK(generation = NONE) @FK SpecialtyNote note,  // Single-column key chain, two levels deep.
            String remark
    ) implements Entity<SpecialtyNote> {}

    public record CycleA(@PK(generation = NONE) @FK CycleB other) implements Entity<CycleB> {}
    public record CycleB(@PK(generation = NONE) @FK CycleA other) implements Entity<CycleA> {}

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
    public record PetType(
            @PK Integer id,
            String name,
            @Nullable String description
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
    public void testInsertAndFetchIdWithSequenceEmptyThrows() {
        // H2 does not support using sequence-based ID generation together with fetch mode.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        assertThrows(PersistenceException.class, () -> repo.insertAndFetchId(PetSequenceEmpty.builder()
                .name("Buddy")
                .birthDate(LocalDate.of(2020, 1, 1))
                .type(PetType.builder().id(1).build())
                .owner(Owner.builder().id(1).build())
                .build()));
    }

    @BeforeEach
    void setUpBranchTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    DROP TABLE IF EXISTS version_long_entity CASCADE;
                    CREATE TABLE version_long_entity (
                        id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name varchar(255),
                        version bigint DEFAULT 0
                    );
                    INSERT INTO version_long_entity (name) VALUES ('Alice');
                    INSERT INTO version_long_entity (name) VALUES ('Bob');
                    """);
                statement.execute("""
                    DROP TABLE IF EXISTS version_instant_entity CASCADE;
                    CREATE TABLE version_instant_entity (
                        id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name varchar(255),
                        version timestamp DEFAULT CURRENT_TIMESTAMP
                    );
                    INSERT INTO version_instant_entity (name) VALUES ('Alice');
                    INSERT INTO version_instant_entity (name) VALUES ('Bob');
                    """);
                statement.execute("""
                    DROP TABLE IF EXISTS pk_only_entity CASCADE;
                    CREATE TABLE pk_only_entity (
                        id integer PRIMARY KEY
                    );
                    INSERT INTO pk_only_entity (id) VALUES (1);
                    INSERT INTO pk_only_entity (id) VALUES (2);
                    """);
                statement.execute("""
                    DROP TABLE IF EXISTS seq_entity CASCADE;
                    DROP SEQUENCE IF EXISTS seq_entity_id_seq;
                    CREATE SEQUENCE seq_entity_id_seq START WITH 1 INCREMENT BY 1;
                    CREATE TABLE seq_entity (
                        id integer DEFAULT NEXT VALUE FOR seq_entity_id_seq NOT NULL PRIMARY KEY,
                        name varchar(255),
                        version integer DEFAULT 0
                    );
                    INSERT INTO seq_entity (name) VALUES ('Alpha');
                    INSERT INTO seq_entity (name) VALUES ('Beta');
                    """);
            }
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

    @Builder(toBuilder = true)
    @DbTable("seq_entity")
    public record SeqEntity(
            @PK(generation = SEQUENCE, sequence = "seq_entity_id_seq") Integer id,
            String name,
            @Version int version
    ) implements Entity<Integer> {}

    @Test
    public void testInsertAndFetchIdWithSeqEntityThrows() {
        // H2 does not support using sequence-based ID generation together with fetch mode.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var entity = SeqEntity.builder()
                .name("Gamma")
                .version(0)
                .build();
        assertThrows(PersistenceException.class, () -> repo.insertAndFetchId(entity));
    }

    @Test
    public void testInsertAndFetchIdsWithSeqEntityThrows() {
        // H2 does not support using sequence-based ID generation together with fetch mode.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var entities = List.of(
                SeqEntity.builder().name("Delta").version(0).build(),
                SeqEntity.builder().name("Epsilon").version(0).build());
        assertThrows(PersistenceException.class, () -> repo.insertAndFetchIds(entities));
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

    @Test
    public void testUuidRemove() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(ApiKey.class);
        long before = repo.count();
        repo.remove(repo.getById(DEFAULT_KEY_ID));
        assertEquals(before - 1, repo.count());
    }
}
