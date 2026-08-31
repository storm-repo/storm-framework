package st.orm.spi.mariadb;

import static java.util.Collections.nCopies;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;
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
public class MariaDBEntityRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    public static MariaDBContainer<?> mariadbContainer = new MariaDBContainer<>("mariadb:11.8")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort());
    ;

    public static DataSource dataSource() {
        return ContainerDataSource.of(mariadbContainer.getJdbcUrl(), mariadbContainer.getUsername(),
                mariadbContainer.getPassword());
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
        // Mariadb is able to update a record with the same unique key, where Oracle throws an exception.
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

    @Test
    public void testRemoveByCompoundPkExpandsWhere() {
        // The compound key expands rather than rendering as the row value tuple the dialect is otherwise willing
        // to emit. MariaDB does not resolve (vet_id, specialty_id) = (?, ?) against the primary key in a DELETE or
        // an UPDATE and scans the table for every row it identifies.
        String expectedSql = """
                DELETE FROM vet_specialty
                WHERE vet_id = ? AND specialty_id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialty.class);
        var pk = VetSpecialtyPK.builder().vetId(1).specialtyId(2).build();
        repo.insert(VetSpecialty.builder().id(pk).build());
        var observed = new AtomicBoolean(false);
        observe(sql -> {
            if (sql.statement().startsWith("DELETE") && !observed.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(1, sql.parameters().get(0).dbValue());
                assertEquals(2, sql.parameters().get(1).dbValue());
            }
        }, () -> repo.removeById(pk));
        assertTrue(observed.get());
        assertTrue(repo.findById(pk).isEmpty());
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
    public void testInsertAndFetchWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                RETURNING id""";
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
            var entity = repo.insertAndFetch(Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
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
    public void testInsertAndFetchWithSequenceBatch() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?), (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                RETURNING id""";
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
            var entities = repo.insertAndFetch(nCopies(2, Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build())).stream().distinct().toList();
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

    @Test
    public void testUpsertWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = VALUES(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)""";
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
            repo.upsert(Pet.builder()
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
    public void testUpsertAndFetchWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = VALUES(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)
                RETURNING id""";
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
            var entity = repo.upsertAndFetch(Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testUpsertAndFetchWithSequenceBatch() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?), (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = VALUES(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)
                RETURNING id""";
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
            var entities = repo.upsertAndFetch(nCopies(2, Pet.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build())).stream().distinct().toList();
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
    public void testUpsertWithSequenceStream() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (NEXT VALUE FOR pet_id_seq, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = VALUES(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)""";
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
            repo.upsert(nCopies(2, Pet.builder()
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
    public void testInsertAndFetchWithSequenceEmpty() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)
                RETURNING id""";
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
            var entity = repo.insertAndFetch(PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
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
    public void testInsertAndFetchWithSequenceEmptyBatch() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?), (?, ?, ?, ?)
                RETURNING id""";
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
            var entities = repo.insertAndFetch(nCopies(2, PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build())).stream().distinct().toList();
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
    public void testUpsertWithSequenceEmptyNew() {
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
            var id = 100;
            var e = assertThrows(PersistenceException.class, () ->
                    repo.upsert(PetSequenceEmpty.builder()
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

    @Test
    public void testUpsertWithSequenceEmpty() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)""";
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
            repo.upsert(PetSequenceEmpty.builder()
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
    public void testUpsertAndFetchWithSequenceEmpty() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)
                RETURNING id""";
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
            var entity = repo.upsertAndFetch(PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build());
            assertNotNull(entity.id());
            assertEquals("Buddy", entity.name());
            assertEquals(LocalDate.of(2020, 1, 1), entity.birthDate());
            assertEquals(1, entity.type().id());
            assertEquals(1, entity.owner().id());
        });
    }

    @Test
    public void testUpsertAndFetchWithSequenceEmptyBatch() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?), (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)
                RETURNING id""";
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
            var entities = repo.upsertAndFetch(nCopies(2, PetSequenceEmpty.builder()
                    .name("Buddy")
                    .birthDate(LocalDate.of(2020, 1, 1))
                    .type(PetType.builder().id(1).build())
                    .owner(Owner.builder().id(1).build())
                    .build())).stream().distinct().toList();
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
    public void testUpsertWithSequenceEmptyStream() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), name = VALUES(name), birth_date = VALUES(birth_date), type_id = VALUES(type_id), owner_id = VALUES(owner_id)""";
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
            repo.upsert(nCopies(2, PetSequenceEmpty.builder()
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
    public void testUpsertAndFetchWithSequenceExisting() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        // First insert a pet and get the id.
        var inserted = repo.insertAndFetch(Pet.builder()
                .name("Buddy")
                .birthDate(LocalDate.of(2020, 1, 1))
                .type(PetType.builder().id(1).build())
                .owner(Owner.builder().id(1).build())
                .build());
        assertNotNull(inserted.id());
        // Now upsert the same pet with an existing non-default id.
        var updated = repo.upsertAndFetch(inserted.toBuilder().name("Max").build());
        assertEquals(inserted.id(), updated.id());
        assertEquals("Max", updated.name());
    }

    @Test
    public void testUpsertAndFetchWithSequenceExistingBatch() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Pet.class);
        // First insert two pets and get the ids.
        var insertedIds = repo.insertAndFetchIds(List.of(
                Pet.builder()
                        .name("Buddy")
                        .birthDate(LocalDate.of(2020, 1, 1))
                        .type(PetType.builder().id(1).build())
                        .owner(Owner.builder().id(1).build())
                        .build(),
                Pet.builder()
                        .name("Rex")
                        .birthDate(LocalDate.of(2020, 2, 1))
                        .type(PetType.builder().id(1).build())
                        .owner(Owner.builder().id(1).build())
                        .build()));
        assertEquals(2, insertedIds.size());
        // Now upsert the same pets with existing non-default ids.
        var updatedEntities = repo.upsertAndFetch(List.of(
                Pet.builder().id(insertedIds.get(0)).name("Max").birthDate(LocalDate.of(2020, 1, 1))
                        .type(PetType.builder().id(1).build()).owner(Owner.builder().id(1).build()).build(),
                Pet.builder().id(insertedIds.get(1)).name("Bella").birthDate(LocalDate.of(2020, 2, 1))
                        .type(PetType.builder().id(1).build()).owner(Owner.builder().id(1).build()).build()
        )).stream().sorted(Comparator.comparingInt(Entity::id)).toList();
        assertEquals(2, updatedEntities.size());
        assertEquals("Max", updatedEntities.get(0).name());
        assertEquals("Bella", updatedEntities.get(1).name());
    }

    @BeforeEach
    void setUpBranchTables() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS version_long_entity");
        jdbc.execute("""
                CREATE TABLE version_long_entity (
                    id integer AUTO_INCREMENT PRIMARY KEY,
                    name varchar(255),
                    version bigint DEFAULT 0
                )""");
        jdbc.execute("INSERT INTO version_long_entity (name) VALUES ('Alice')");
        jdbc.execute("INSERT INTO version_long_entity (name) VALUES ('Bob')");

        jdbc.execute("DROP TABLE IF EXISTS version_instant_entity");
        jdbc.execute("""
                CREATE TABLE version_instant_entity (
                    id integer AUTO_INCREMENT PRIMARY KEY,
                    name varchar(255),
                    version timestamp DEFAULT CURRENT_TIMESTAMP
                )""");
        jdbc.execute("INSERT INTO version_instant_entity (name) VALUES ('Alice')");
        jdbc.execute("INSERT INTO version_instant_entity (name) VALUES ('Bob')");

        jdbc.execute("DROP TABLE IF EXISTS non_autogen_entity");
        jdbc.execute("""
                CREATE TABLE non_autogen_entity (
                    id integer PRIMARY KEY,
                    name varchar(255),
                    version integer DEFAULT 0
                )""");
        jdbc.execute("INSERT INTO non_autogen_entity (id, name) VALUES (1, 'First')");
        jdbc.execute("INSERT INTO non_autogen_entity (id, name) VALUES (2, 'Second')");

        jdbc.execute("DROP TABLE IF EXISTS seq_entity");
        jdbc.execute("DROP SEQUENCE IF EXISTS seq_entity_id_seq");
        jdbc.execute("CREATE SEQUENCE seq_entity_id_seq START WITH 1 INCREMENT BY 1");
        jdbc.execute("""
                CREATE TABLE seq_entity (
                    id integer PRIMARY KEY DEFAULT (NEXT VALUE FOR seq_entity_id_seq),
                    name varchar(255),
                    version integer DEFAULT 0
                )""");
        jdbc.execute("INSERT INTO seq_entity (name) VALUES ('Alpha')");
        jdbc.execute("INSERT INTO seq_entity (name) VALUES ('Beta')");
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
            @PK(generation = SEQUENCE, sequence = "seq_entity_id_seq") Integer id,
            String name,
            @Version int version
    ) implements Entity<Integer> {}

    @Test
    public void testInsertAndFetchIdWithSequence() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertTrue(sql.statement().contains("RETURNING id"));
            }
        }, () -> {
            var id = repo.insertAndFetchId(SeqEntity.builder().name("Gamma").version(0).build());
            assertNotNull(id);
            assertTrue(id > 0);
        });
    }

    @Test
    public void testInsertAndFetchIdsWithSequence() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var ids = repo.insertAndFetchIds(List.of(
                SeqEntity.builder().name("Delta").version(0).build(),
                SeqEntity.builder().name("Epsilon").version(0).build()));
        assertEquals(2, ids.size());
        assertTrue(ids.get(0) > 0);
        assertTrue(ids.get(1) > 0);
    }

    @Test
    public void testUpsertAndFetchIdWithSequence() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertTrue(sql.statement().contains("RETURNING id"));
            }
        }, () -> {
            var id = repo.upsertAndFetchId(SeqEntity.builder().name("Zeta").version(0).build());
            assertNotNull(id);
            assertTrue(id > 0);
        });
    }

    @Test
    public void testUpsertAndFetchIdsWithSequenceNew() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var ids = repo.upsertAndFetchIds(List.of(
                SeqEntity.builder().name("Eta").version(0).build(),
                SeqEntity.builder().name("Theta").version(0).build()));
        assertEquals(2, ids.size());
        assertTrue(ids.get(0) > 0);
        assertTrue(ids.get(1) > 0);
    }

    @Test
    public void testUpsertAndFetchIdsWithSequenceExisting() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var existing = repo.findAll();
        var updates = existing.stream()
                .map(entity -> entity.toBuilder().name(entity.name() + " Updated").build())
                .toList();

        var ids = repo.upsertAndFetchIds(updates);
        assertEquals(existing.size(), ids.size());
        for (int i = 0; i < ids.size(); i++) {
            assertEquals(existing.get(i).id(), ids.get(i));
        }
    }

    @Test
    public void testUpsertAndFetchIdsWithSequenceMixed() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(SeqEntity.class);
        var existing = repo.getById(1);
        var entities = List.of(
                SeqEntity.builder().name("Iota").version(0).build(),
                existing.toBuilder().name("Alpha Updated").build());

        var ids = repo.upsertAndFetchIds(entities);
        assertEquals(2, ids.size());
        assertEquals(existing.id(), ids.get(1));
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
    public void testUpsertNewEntityRoutesToInsert() {
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VersionLongEntity.class);
        repo.upsert(VersionLongEntity.builder().name("New Entity").version(0L).build());
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

    // Entity callbacks on the dialect-specific sequence insert paths.

    @Test
    public void testSequenceInsertAndFetchIdFiresCallbacksWithGeneratedKey() {
        var observed = new java.util.ArrayList<SeqEntity>();
        var orm = PreparedStatementTemplate.ORM(dataSource).withEntityCallback(new st.orm.EntityCallback<SeqEntity>() {
            @Override
            public SeqEntity beforeInsert(SeqEntity entity) {
                return entity.toBuilder().name(entity.name().toUpperCase()).build();
            }

            @Override
            public void afterInsert(SeqEntity entity) {
                observed.add(entity);
            }
        });
        var repo = orm.entity(SeqEntity.class);
        // The RETURNING path for sequence keys is dialect-specific; it must still run the callbacks.
        var id = repo.insertAndFetchId(SeqEntity.builder().name("callback seq").version(0).build());
        assertEquals("CALLBACK SEQ", repo.getById(id).name());
        assertEquals(1, observed.size());
        assertEquals(id, observed.getFirst().id());
    }

    @Test
    public void testSequenceBatchInsertAndFetchIdsFiresCallbacksWithGeneratedKeys() {
        var observed = new java.util.ArrayList<SeqEntity>();
        var orm = PreparedStatementTemplate.ORM(dataSource).withEntityCallback(new st.orm.EntityCallback<SeqEntity>() {
            @Override
            public SeqEntity beforeInsert(SeqEntity entity) {
                return entity.toBuilder().name(entity.name().toUpperCase()).build();
            }

            @Override
            public void afterInsert(SeqEntity entity) {
                observed.add(entity);
            }
        });
        var repo = orm.entity(SeqEntity.class);
        var ids = repo.insertAndFetchIds(List.of(
                SeqEntity.builder().name("cb batch one").version(0).build(),
                SeqEntity.builder().name("cb batch two").version(0).build()));
        assertEquals(2, ids.size());
        assertEquals("CB BATCH ONE", repo.getById(ids.get(0)).name());
        assertEquals(ids, observed.stream().map(SeqEntity::id).toList());
        assertEquals(List.of("CB BATCH ONE", "CB BATCH TWO"), observed.stream().map(SeqEntity::name).toList());
    }
}
