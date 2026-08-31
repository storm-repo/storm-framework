package st.orm.spi.h2;

import static java.util.Collections.nCopies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.template.SqlInterceptor.observe;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
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

    @Test
    public void testUpsertDependentOneToOne() {
        // The PK is the FK to specialty and the entity carries a temporal column: the MERGE source query must
        // cast its parameters for H2 to accept them, resolving the FK column type via the referenced PK.
        var specialty = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class).getById(1);
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SpecialtyNote.class);
        repo.upsert(SpecialtyNote.builder()
                .specialty(specialty)
                .note("first")
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build());
        var stored = repo.getById(specialty);
        assertEquals("first", stored.note());
        repo.upsert(stored.toBuilder()
                .note("second")
                .updatedAt(Instant.parse("2026-01-02T10:00:00Z"))
                .build());
        var updated = repo.getById(specialty);
        assertEquals("second", updated.note());
        assertEquals(Instant.parse("2026-01-02T10:00:00Z"), updated.updatedAt());
    }

    @Test
    public void testUpsertDependentOneToOneBatch() {
        var specialtyRepo = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class);
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SpecialtyNote.class);
        var notes = List.of(
                SpecialtyNote.builder()
                        .specialty(specialtyRepo.getById(2))
                        .note("surgery note")
                        .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                        .build(),
                SpecialtyNote.builder()
                        .specialty(specialtyRepo.getById(3))
                        .note("dentistry note")
                        .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                        .build());
        repo.upsert(notes);
        repo.upsert(notes.stream().map(n -> n.toBuilder().note("%s updated".formatted(n.note())).build()).toList());
        assertEquals("surgery note updated", repo.getById(specialtyRepo.getById(2)).note());
        assertEquals("dentistry note updated", repo.getById(specialtyRepo.getById(3)).note());
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

    @Builder(toBuilder = true)
    @DbTable("vet_specialty_note")
    public record VetSpecialtyNote(
            @PK(generation = NONE) @FK VetSpecialty vetSpecialty,  // The PK is a compound FK spanning two columns.
            String note
    ) implements Entity<VetSpecialty> {}

    @Test
    public void testUpsertCompoundForeignKeyAsPrimaryKey() {
        // The MERGE source query must resolve each foreign key column to its leaf type within the referenced
        // compound key for the H2 casts to apply.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialtyNote.class);
        var vetSpecialty = new VetSpecialty(new VetSpecialtyPK(2, 1));
        repo.upsert(VetSpecialtyNote.builder().vetSpecialty(vetSpecialty).note("first").build());
        assertEquals("first", repo.getById(vetSpecialty).note());
        repo.upsert(VetSpecialtyNote.builder().vetSpecialty(vetSpecialty).note("second").build());
        assertEquals("second", repo.getById(vetSpecialty).note());
    }

    @Builder(toBuilder = true)
    @DbTable("vet_specialty_note_audit")
    public record VetSpecialtyNoteAudit(
            @PK(generation = NONE) @FK VetSpecialtyNote note,  // The referenced key chain is two levels deep.
            String remark
    ) implements Entity<VetSpecialtyNote> {}

    @Test
    public void testCrudNestedCompoundKeyChain() {
        // The FK resolves through VetSpecialtyNote's PK — the VetSpecialty entity keyed by the compound
        // VetSpecialtyPK record — flattening to the (vet_id, specialty_id) columns.
        var noteRepo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialtyNote.class);
        var vetSpecialty = new VetSpecialty(new VetSpecialtyPK(3, 2));
        noteRepo.upsert(VetSpecialtyNote.builder().vetSpecialty(vetSpecialty).note("base note").build());
        var note = noteRepo.getById(vetSpecialty);

        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialtyNoteAudit.class);
        repo.insert(VetSpecialtyNoteAudit.builder().note(note).remark("created").build());
        var stored = repo.getById(note);
        assertEquals("created", stored.remark());
        assertEquals(vetSpecialty.id(), stored.note().vetSpecialty().id());
        repo.update(stored.toBuilder().remark("updated").build());
        assertEquals("updated", repo.getById(note).remark());
        repo.remove(stored.toBuilder().remark("updated").build());
        assertTrue(repo.findById(note).isEmpty());
    }

    @Test
    public void testUpsertNestedCompoundKeyChain() {
        var noteRepo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialtyNote.class);
        var vetSpecialty = new VetSpecialty(new VetSpecialtyPK(4, 2));
        noteRepo.upsert(VetSpecialtyNote.builder().vetSpecialty(vetSpecialty).note("base note").build());
        var note = noteRepo.getById(vetSpecialty);

        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialtyNoteAudit.class);
        repo.upsert(VetSpecialtyNoteAudit.builder().note(note).remark("created").build());
        assertEquals("created", repo.getById(note).remark());
        repo.upsert(VetSpecialtyNoteAudit.builder().note(note).remark("revised").build());
        assertEquals("revised", repo.getById(note).remark());
    }

    @Builder(toBuilder = true)
    @DbTable("specialty_note_history")
    public record SpecialtyNoteHistory(
            @PK(generation = NONE) @FK SpecialtyNote note,  // Single-column key chain, two levels deep.
            String remark
    ) implements Entity<SpecialtyNote> {}

    @Test
    public void testUpsertNestedSingleColumnKeyChain() {
        // The chain SpecialtyNote -> Specialty -> Integer collapses to a single column named after the field.
        var specialty = PreparedStatementTemplate.ORM(dataSource).entity(Specialty.class).getById(3);
        var noteRepo = PreparedStatementTemplate.ORM(dataSource).entity(SpecialtyNote.class);
        noteRepo.upsert(SpecialtyNote.builder()
                .specialty(specialty)
                .note("dentistry note")
                .updatedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build());
        var note = noteRepo.getById(specialty);

        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SpecialtyNoteHistory.class);
        repo.upsert(SpecialtyNoteHistory.builder().note(note).remark("created").build());
        assertEquals("created", repo.getById(note).remark());
        repo.upsert(SpecialtyNoteHistory.builder().note(note).remark("revised").build());
        assertEquals("revised", repo.getById(note).remark());
    }

    public record CycleA(@PK(generation = NONE) @FK CycleB other) implements Entity<CycleB> {}
    public record CycleB(@PK(generation = NONE) @FK CycleA other) implements Entity<CycleA> {}

    @Test
    public void testCircularKeyChainFailsFast() {
        // A key chain that references itself cannot be flattened; model construction must fail with a clear
        // message instead of looping or emitting a broken model.
        assertThrows(PersistenceException.class, () ->
                PreparedStatementTemplate.ORM(dataSource).entity(CycleA.class).findAll());
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
    public record PetType(
            @PK Integer id,
            String name,
            @Nullable String description
    ) implements Entity<Integer> {}

    @Test
    public void testInsertWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            var entity = repo.findAll().stream().max(Comparator.comparingInt(Pet::id)).orElseThrow();
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testInsertAndFetchIdWithSequenceThrows() {
        // H2 does not support using sequence-based ID generation together with fetch mode.
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        assertThrows(PersistenceException.class, () -> repo.insertAndFetchId(Pet.builder()
                .name("Buddy")
                .birthDate(LocalDate.of(2020, 1, 1))
                .type(PetType.builder().id(1).build())
                .owner(Owner.builder().id(1).build())
                .build()));
    }

    @Test
    public void testInsertAndFetchWithSequenceIgnoreAutoGenerate() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(Pet.builder()
                    .id(100)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build(), true);
            var entity = repo.getById(100);
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testInsertWithSequenceStream() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(nCopies(2, Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).stream());
            var entities = repo.findAll().stream().sorted(Comparator.comparingInt(Pet::id)).skip(13).toList();
            assertEquals(2, entities.size());
            for (var entity : entities) {
                assertNotNull(entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            }
        });
    }

    @Test
    public void testInsertWithSequenceIgnoreAutoGenerateBatch() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isEmpty());
            }
        }, () -> {
            var ids = List.of(100, 101);
            repo.insert(ids.stream().map(id -> Pet.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).toList(), true);
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testInsertWithSequenceIgnoreAutoGenerateStream() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isEmpty());
            }
        }, () -> {
            var ids = List.of(100, 101);
            repo.insert(ids.stream().map(id -> Pet.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()), true);
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceExisting() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            var id = 1;
            repo.upsert(Pet.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            var entity = repo.getById(id);
            assertEquals(id, entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testUpsertWithSequenceExistingBatch() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(1, 2);
            repo.upsert(ids.stream().map(id -> Pet.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).toList());
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceExistingStream() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(1, 2);
            repo.upsert(ids.stream().map(id -> Pet.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()));
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceNew() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            var id = 100;
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(Pet.builder()
                            .id(id)
                            .name("Buddy")
                            .birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build())
                            .owner(Owner.builder().id(1).build())
                            .build()));
            assertNull(e.getCause(), "Exception must be raised by storm.");
        });
    }

    @Test
    public void testUpsertWithSequenceNewBatch() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(100, 101);
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(ids.stream().map(id -> Pet.builder()
                            .id(id)
                            .name("Buddy")
                            .birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build())
                            .owner(Owner.builder().id(1).build())
                            .build()).toList()));
            assertNull(e.getCause(), "Exception must be raised by storm.");
        });
    }

    @Test
    public void testUpsertWithSequenceNewStream() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(100, 101);
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(ids.stream().map(id -> Pet.builder()
                            .id(id)
                            .name("Buddy")
                            .birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build())
                            .owner(Owner.builder().id(1).build())
                            .build())));
            assertNull(e.getCause(), "Exception must be raised by storm.");
        });
    }

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
    public void testInsertWithSequenceEmpty() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            var entity = repo.findAll().stream().max(Comparator.comparingInt(PetSequenceEmpty::id)).orElseThrow();
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

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

    @Test
    public void testInsertWithSequenceEmptyIgnoreAutoGenerate() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(PetSequenceEmpty.builder()
                    .id(100)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build(), true);
            var entity = repo.getById(100);
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testInsertWithSequenceEmptyStream() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.insert(nCopies(2, PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).stream());
            var entities = repo.findAll().stream().sorted(Comparator.comparingInt(PetSequenceEmpty::id)).skip(13).toList();
            assertEquals(2, entities.size());
            for (var entity : entities) {
                assertNotNull(entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            }
        });
    }

    @Test
    public void testInsertWithSequenceEmptyIgnoreAutoGenerateBatch() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isEmpty());
            }
        }, () -> {
            var ids = List.of(100, 101);
            repo.insert(ids.stream().map(id -> PetSequenceEmpty.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).toList(), true);
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testInsertWithSequenceEmptyIgnoreAutoGenerateStream() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isEmpty());
            }
        }, () -> {
            var ids = List.of(100, 101);
            repo.insert(ids.stream().map(id -> PetSequenceEmpty.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()), true);
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceEmptyExisting() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertFalse(sql.bindVariables().isPresent());
            }
        }, () -> {
            var id = 1;
            repo.upsert(PetSequenceEmpty.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            var entity = repo.getById(id);
            assertEquals(id, entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testUpsertWithSequenceEmptyExistingBatch() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(1, 2);
            repo.upsert(ids.stream().map(id -> PetSequenceEmpty.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()).toList());
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceEmptyExistingStream() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(1, 2);
            repo.upsert(ids.stream().map(id -> PetSequenceEmpty.builder()
                    .id(id)
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build()));
            ids.forEach(id -> {
                var entity = repo.getById(id);
                assertEquals(id, entity.id());
                assertEquals("Buddy", entity.name());
                assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
                assertEquals(1, entity.type().id());
                assertEquals(1, entity.owner().id());
            });
        });
    }

    @Test
    public void testUpsertWithSequenceEmptyNewBatch() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(100, 101);
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(ids.stream().map(id -> PetSequenceEmpty.builder()
                            .id(id)
                            .name("Buddy")
                            .birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build())
                            .owner(Owner.builder().id(1).build())
                            .build()).toList()));
            assertNull(e.getCause(), "Exception must be raised by storm.");
        });
    }

    @Test
    public void testUpsertWithSequenceEmptyNewStream() {
        String expectedSql = """
                UPDATE pet
                SET name = ?, birth_date = ?, type_id = ?, owner_id = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(PetSequenceEmpty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var ids = List.of(100, 101);
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(ids.stream().map(id -> PetSequenceEmpty.builder()
                            .id(id)
                            .name("Buddy")
                            .birthDate(LocalDate.of(2020, 1, 1))
                            .type(PetType.builder().id(1).build())
                            .owner(Owner.builder().id(1).build())
                            .build())));
            assertNull(e.getCause(), "Exception must be raised by storm.");
        });
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
