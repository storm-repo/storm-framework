package st.orm.spi.postgresql;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
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

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.nCopies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.util.AssertionErrors.assertNull;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.core.template.SqlInterceptor.observe;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)    // Prevent swapping to H2.
@DataJpaTest(showSql = false)
@Testcontainers
public class PostgreSQLEntityRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort());

    // Dynamically inject properties into the Spring Boot context
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    private DataSource dataSource;

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
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull Address address,
            @Nullable String telephone,
            @Version int version
    ) implements Entity<Integer> {}

    @Test
    public void testSelectLimit() {
        String expectedSql = """
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                LIMIT 2""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            var entities = repo.select().limit(2).getResultList();
            assertEquals(2, entities.size());
            assertEquals("Betty", entities.getFirst().firstName());
            assertEquals("Davis", entities.getFirst().lastName());
            assertEquals("638 Cardinal Ave.", entities.getFirst().address().address());
            assertEquals("Sun Prairie", entities.getFirst().address().city());
            assertEquals("6085551749", entities.getFirst().telephone());
            assertEquals(0, entities.getFirst().version());
            assertEquals("George", entities.getLast().firstName());
            assertEquals("Franklin", entities.getLast().lastName());
            assertEquals("110 W. Liberty St.", entities.getLast().address().address());
            assertEquals("Madison", entities.getLast().address().city());
            assertEquals("6085551023", entities.getLast().telephone());
            assertEquals(0, entities.getLast().version());
        });
    }

    @Test
    public void testSelectLimitOffset() {
        String expectedSql = """
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1 LIMIT 2""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            var entities = repo.select().orderBy(Metamodel.of(Owner.class, "id")).offset(1).limit(2).getResultList();
            assertEquals(2, entities.size());
            assertEquals("George", entities.getFirst().firstName());
            assertEquals("Franklin", entities.getFirst().lastName());
            assertEquals("110 W. Liberty St.", entities.getFirst().address().address());
            assertEquals("Madison", entities.getFirst().address().city());
            assertEquals("6085551023", entities.getFirst().telephone());
            assertEquals(0, entities.getFirst().version());
            assertEquals("Eduardo", entities.getLast().firstName());
            assertEquals("Rodriquez", entities.getLast().lastName());
            assertEquals("2693 Commerce St.", entities.getLast().address().address());
            assertEquals("McFarland", entities.getLast().address().city());
            assertEquals("6085558763", entities.getLast().telephone());
            assertEquals(0, entities.getLast().version());
        });
    }

    @Test
    public void testSelectOffset() {
        String expectedSql = """
                SELECT o.id, o.first_name, o.last_name, o.address, o.city, o.telephone, o.version
                FROM owner o
                ORDER BY o.id
                OFFSET 1""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            var entities = repo.select().orderBy(Metamodel.of(Owner.class, "id")).offset(1).getResultList();
            assertEquals(9, entities.size());
            assertEquals("George", entities.getFirst().firstName());
            assertEquals("Franklin", entities.getFirst().lastName());
            assertEquals("110 W. Liberty St.", entities.getFirst().address().address());
            assertEquals("Madison", entities.getFirst().address().city());
            assertEquals("6085551023", entities.getFirst().telephone());
        });
    }

    @Test
    public void testInsertAndFetch() {
        String expectedSql = """
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of("id"));
                assertFalse(sql.versionAware());
                assertEquals("John", sql.parameters().get(0).dbValue());
                assertEquals("Doe", sql.parameters().get(1).dbValue());
            }
        }, () -> {
            var entity = repo.insertAndFetch(Vet.builder().firstName("John").lastName("Doe").build());
            assertTrue(entity.id() > 0);
            assertEquals("John", entity.firstName());
            assertEquals("Doe", entity.lastName());
        });
    }

    @Test
    public void testInsertAndFetchInline() {
        String expectedSql = """
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of("id"));
                assertFalse(sql.versionAware());
                assertEquals("John", sql.parameters().get(0).dbValue());
                assertEquals("Doe", sql.parameters().get(1).dbValue());
            }
        }, () -> {
            var entity = repo.insertAndFetch(Owner.builder().firstName("John").lastName("Doe").address(Address.builder().address("243 Acalanes Dr").city("Sunnyvale").build()).build());
            assertTrue(entity.id() > 0);
            assertEquals("John", entity.firstName());
            assertEquals("Doe", entity.lastName());
            assertEquals("243 Acalanes Dr", entity.address().address());
            assertEquals("Sunnyvale", entity.address().city());
            assertNull("telephone", entity.telephone());
            assertEquals(0, entity.version());
        });
    }

    @Test
    public void testInsertAndFetchInlineBatch() {
        String expectedSql = """
                INSERT INTO owner (first_name, last_name, address, city, telephone, version)
                VALUES (?, ?, ?, ?, ?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of("id"));
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var entities = repo.insertAndFetch(List.of(
                    Owner.builder().firstName("John").lastName("Doe").address(Address.builder().address("243 Acalanes Dr").city("Sunnyvale").build()).build(),
                    Owner.builder().firstName("Jane").lastName("Doe").address(Address.builder().address("243 Acalanes Dr").city("Sunnyvale").build()).build()
            )).stream().sorted(Comparator.comparingInt(Owner::id)).toList();
            assertEquals(2, entities.size());
            assertEquals("John", entities.getFirst().firstName());
            assertEquals("Doe", entities.getFirst().lastName());
            assertEquals("243 Acalanes Dr", entities.getFirst().address().address());
            assertEquals("Sunnyvale", entities.getFirst().address().city());
            assertNull("telephone", entities.getFirst().telephone());
            assertEquals(0, entities.getFirst().version());
            assertEquals("Jane", entities.getLast().firstName());
            assertEquals("Doe", entities.getLast().lastName());
            assertEquals("243 Acalanes Dr", entities.getLast().address().address());
            assertEquals("Sunnyvale", entities.getLast().address().city());
            assertNull("telephone", entities.getLast().telephone());
            assertEquals(0, entities.getLast().version());
        });
    }

    @Test
    public void testInsertAndFetchBatch() {
        String expectedSql = """
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of("id"));
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var entities = repo.insertAndFetch(List.of(
                    Vet.builder().firstName("John").lastName("Doe").build(),
                    Vet.builder().firstName("Jane").lastName("Doe").build()
            )).stream().sorted(Comparator.comparingInt(Entity::id)).toList();
            assertEquals(2, entities.size());
            assertEquals("John", entities.getFirst().firstName());
            assertEquals("Doe", entities.getFirst().lastName());
            assertEquals("Jane", entities.getLast().firstName());
            assertEquals("Doe", entities.getLast().lastName());
        });
    }

    @Test
    public void testUpdateAndFetchInlineVersion() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE (id, version) IN ((?, ?))""";
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
            var update = repo.updateAndFetch(entity.toBuilder().lastName("Smith").build());
            assertEquals("Betty", update.firstName());
            assertEquals("Smith", update.lastName());
            assertEquals("638 Cardinal Ave.", update.address().address());
            assertEquals("Sun Prairie", update.address().city());
            assertEquals("6085551749", update.telephone());
            assertEquals(1, update.version());
        });
    }

    @Test
    public void testUpdateAndFetchInlineVersionBatch() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var entities = repo.findAllById(List.of(1, 2));
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertTrue(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var updates = repo.updateAndFetch(
                    entities.stream().map(entity -> entity.toBuilder().lastName("Smith").build()).toList()
            ).stream().sorted(Comparator.comparingInt(Entity::id)).toList();
            assertEquals(2, updates.size());
            assertEquals("Betty", updates.getFirst().firstName());
            assertEquals("Smith", updates.getFirst().lastName());
            assertEquals("638 Cardinal Ave.", updates.getFirst().address().address());
            assertEquals("Sun Prairie", updates.getFirst().address().city());
            assertEquals("6085551749", updates.getFirst().telephone());
            assertEquals(1, updates.getFirst().version());
            assertEquals("George", updates.getLast().firstName());
            assertEquals("Smith", updates.getLast().lastName());
            assertEquals("110 W. Liberty St.", updates.getLast().address().address());
            assertEquals("Madison", updates.getLast().address().city());
            assertEquals("6085551023", updates.getLast().telephone());
            assertEquals(1, updates.getLast().version());
        });
    }

    @Test
    public void testUpsertAndFetchBatch() {
        String expectedSql = """
                UPDATE vet
                SET first_name = ?, last_name = ?
                WHERE id = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
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
                    Vet.builder().id(1).firstName("John").lastName("Doe").build(),
                    Vet.builder().id(2).firstName("Jane").lastName("Doe").build()
            )).stream().sorted(Comparator.comparingInt(Entity::id)).toList();
            assertEquals(2, entities.size());
            assertEquals("John", entities.getFirst().firstName());
            assertEquals("Doe", entities.getFirst().lastName());
            assertEquals("Jane", entities.getLast().firstName());
            assertEquals("Doe", entities.getLast().lastName());
        });
    }

    @Test
    public void testUpsert() {
        String expectedSql = """
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertEquals(sql.generatedKeys(), List.of("id"));
            assertFalse(sql.versionAware());
            assertEquals("John", sql.parameters().get(0).dbValue());
            assertEquals("Doe", sql.parameters().get(1).dbValue());
        }, () -> repo.upsert(Vet.builder().firstName("John").lastName("Doe").build()));
        var entity = repo.select().where(Metamodel.of(Vet.class, "firstName"), EQUALS, "John").getSingleResult();
        repo.upsert(entity.toBuilder().lastName("Smith").build());
        var updated = repo.select().where(Metamodel.of(Vet.class, "firstName"), EQUALS, "John").getSingleResult();
        assertEquals(entity.id(), updated.id());
        assertEquals("John", updated.firstName());
        assertEquals("Smith", updated.lastName());
    }

    @Test
    public void testUpsertBatch() {
        String expectedSql = """
                INSERT INTO vet (first_name, last_name)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Vet.class);
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertEquals(sql.generatedKeys(), List.of("id"));
            assertFalse(sql.versionAware());
            assertTrue(sql.bindVariables().isPresent());
        }, () -> repo.upsert(List.of(
                Vet.builder().firstName("John").lastName("Doe").build(),
                Vet.builder().firstName("Jane").lastName("Doe").build())));
        var entities = repo.select().where(Metamodel.of(Vet.class, "lastName"), EQUALS, "Doe").getResultList();
        repo.upsert(entities.stream().map(entity -> entity.toBuilder().lastName("Smith").build()).toList());
        var updated = repo.select().where(Metamodel.of(Vet.class, "lastName"), EQUALS, "Smith").getResultList();
        var none = repo.select().where(Metamodel.of(Vet.class, "lastName"), EQUALS, "Doe").getResultCount();
        assertEquals(2, updated.size());
        assertTrue(updated.stream().allMatch(entity -> entity.lastName().equals("Smith")));
        assertEquals(0, none);
    }

    @Test
    public void testUpsertInlineVersion() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE (id, version) IN ((?, ?))""";
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
    public void testUpsertAndFetchInlineVersion() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE (id, version) IN ((?, ?))""";
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
            var update = repo.upsertAndFetch(entity.toBuilder().lastName("Smith").build());
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

    @Test
    public void testUpsertInlineVersionBatch() {
        String expectedSql = """
                UPDATE owner
                SET first_name = ?, last_name = ?, address = ?, city = ?, telephone = ?, version = version + 1
                WHERE id = ? AND version = ?""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(Owner.class);
        var entities = repo.findAllById(List.of(1, 2));
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertTrue(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            repo.upsert(
                    entities.stream().map(entity -> entity.toBuilder().lastName("Smith").build()).toList()
            );
            var updates = repo.findAllById(List.of(1, 2)).stream().sorted(Comparator.comparingInt(Entity::id)).toList();
            assertEquals(2, updates.size());
            assertEquals("Betty", updates.getFirst().firstName());
            assertEquals("Smith", updates.getFirst().lastName());
            assertEquals("638 Cardinal Ave.", updates.getFirst().address().address());
            assertEquals("Sun Prairie", updates.getFirst().address().city());
            assertEquals("6085551749", updates.getFirst().telephone());
            assertEquals(1, updates.getFirst().version());
            assertEquals("George", updates.getLast().firstName());
            assertEquals("Smith", updates.getLast().lastName());
            assertEquals("110 W. Liberty St.", updates.getLast().address().address());
            assertEquals("Madison", updates.getLast().address().city());
            assertEquals("6085551023", updates.getLast().telephone());
            assertEquals(1, updates.getLast().version());
        });
    }

    @Builder(toBuilder = true)
    public record PetType(
            @PK Integer id,
            @Nonnull String name,
            @Nullable String description
    ) implements Entity<Integer> {}

    @Test
    public void testUpsertUniqueKey() {
        // Mysql is able to update a record with the same unique key, where PostgreSQL throws an exception.
        // This use case may be handled in the future by specifying @UK (unique constraint) in the entity.
        String expectedSql = """
                INSERT INTO pet_type (name, description)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description""";
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
        var e = assertThrows(PersistenceException.class, () -> repo.upsert(PetType.builder().name("dragon").description("description").build()));
        assertInstanceOf(PSQLException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    public record Specialty(
            @PK(generation = NONE) Integer id,
            @Nonnull String name
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
            @Nonnull @PK(generation = NONE) VetSpecialtyPK id,  // Implicitly @Inlined
            @Nonnull @Persist(insertable = false, updatable = false) @FK Vet vet,
            @Nonnull @Persist(insertable = false, updatable = false) @FK Specialty specialty) implements Entity<VetSpecialtyPK> {
        public VetSpecialty(@Nonnull VetSpecialtyPK pk) {
            //noinspection DataFlowIssue
            this(pk, null, null);
        }
    }

    @Test
    public void testInsertAndFetchCompoundPk() {
        String expectedSql = """
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertEquals(1, sql.parameters().get(0).dbValue());
                assertEquals(2, sql.parameters().get(1).dbValue());
            }
        }, () -> {
            var entity = repo.insertAndFetch(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(1).specialtyId(2).build()).build());
            assertEquals(1, entity.id().vetId());
            assertEquals(2, entity.id().specialtyId());
        });
    }

    @Test
    public void testInsertAndFetchBatchCompoundPk() {
        String expectedSql = """
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialty.class);
        var first = new AtomicBoolean(false);
        observe(sql -> {
            if (!first.getAndSet(true)) {
                assertEquals(expectedSql, sql.statement());
                assertEquals(sql.generatedKeys(), List.of());
                assertFalse(sql.versionAware());
                assertTrue(sql.bindVariables().isPresent());
            }
        }, () -> {
            var entities = repo.insertAndFetch(List.of(
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(1).specialtyId(2).build()).build(),
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(6).specialtyId(3).build()).build()
            )).stream().sorted(Comparator.comparingInt(a -> a.id().vetId())).toList();
            assertEquals(2, entities.size());
            assertEquals(1, entities.getFirst().id().vetId());
            assertEquals(2, entities.getFirst().id().specialtyId());
            assertEquals(6, entities.getLast().id().vetId());
            assertEquals(3, entities.getLast().id().specialtyId());
        });
    }

    @Test
    public void testUpsertAndFetchBatchNewCompoundPk() {
        String expectedSql = """
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)
                ON CONFLICT (vet_id, specialty_id) DO NOTHING""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialty.class);
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
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(1).specialtyId(2).build()).vet(Vet.builder().id(1).build()).specialty(Specialty.builder().id(2).build()).build(),
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(6).specialtyId(3).build()).vet(Vet.builder().id(6).build()).specialty(Specialty.builder().id(3).build()).build()
            )).stream().sorted(Comparator.comparingInt(a -> a.id().vetId())).toList();
            assertEquals(2, entities.size());
            assertEquals(1, entities.getFirst().id().vetId());
            assertEquals(2, entities.getFirst().id().specialtyId());
            assertEquals(6, entities.getLast().id().vetId());
            assertEquals(3, entities.getLast().id().specialtyId());
        });
    }

    @Test
    public void testUpsertAndFetchBatchExistingCompoundPk() {
        String expectedSql = """
                INSERT INTO vet_specialty (vet_id, specialty_id)
                VALUES (?, ?)
                ON CONFLICT (vet_id, specialty_id) DO NOTHING""";
        var repo = PreparedStatementTemplate.ORM(dataSource).entity(VetSpecialty.class);
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
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).vet(Vet.builder().id(1).build()).specialty(Specialty.builder().id(1).build()).build(),
                    VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(3).specialtyId(2).build()).vet(Vet.builder().id(3).build()).specialty(Specialty.builder().id(2).build()).build()
            )).stream().sorted(Comparator.comparingInt(a -> a.id().vetId())).toList();
            assertEquals(2, entities.size());
            assertEquals(2, entities.getFirst().id().vetId());
            assertEquals(1, entities.getFirst().id().specialtyId());
            assertEquals(3, entities.getLast().id().vetId());
            assertEquals(2, entities.getLast().id().specialtyId());
        });
    }

    @Builder(toBuilder = true)
    @DbTable("pet")
    public record Pet(
            @PK(generation = SEQUENCE, sequence = "pet_id_seq") Integer id,
            @Nonnull String name,
            @Nonnull LocalDate birthDate,
            @Nonnull @FK PetType type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Test
    public void testInsertWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)""";
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?), (nextval('pet_id_seq'), ?, ?, ?, ?)
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)""";
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
                VALUES (?, ?, ?, ?, ?)""";
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
                VALUES (?, ?, ?, ?, ?)""";
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
            assertNull("Exception must be raised by storm.", e.getCause());
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
            assertNull("Exception must be raised by storm.", e.getCause());
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
            assertNull("Exception must be raised by storm.", e.getCause());
        });
    }

    @Test
    public void testUpsertWithSequence() {
        String expectedSql = """
                INSERT INTO pet (id, name, birth_date, type_id, owner_id)
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id""";
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?), (nextval('pet_id_seq'), ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id
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
                VALUES (nextval('pet_id_seq'), ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id""";
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
            @Nonnull String name,
            @Nonnull LocalDate birthDate,
            @Nonnull @FK PetType type,
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
                VALUES (?, ?, ?, ?, ?)""";
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
                VALUES (?, ?, ?, ?, ?)""";
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
            assertNull("Exception must be raised by storm.", e.getCause());
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
            assertNull("Exception must be raised by storm.", e.getCause());
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
            assertNull("Exception must be raised by storm.", e.getCause());
        });
    }

    @Test
    public void testUpsertWithSequenceEmpty() {
        String expectedSql = """
                INSERT INTO pet (name, birth_date, type_id, owner_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id""";
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
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id
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
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id
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
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birth_date = EXCLUDED.birth_date, type_id = EXCLUDED.type_id, owner_id = EXCLUDED.owner_id""";
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
}
