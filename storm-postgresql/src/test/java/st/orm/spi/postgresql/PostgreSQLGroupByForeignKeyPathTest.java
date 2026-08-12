package st.orm.spi.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;

import java.time.LocalDate;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * PostgreSQL enforces the GROUP BY rule, so it is where naming the wrong one of two equal columns is visible.
 *
 * <p>A path to a referenced table's key normally resolves to the foreign key column on the referencing table. When
 * the SELECT also projects the referenced table, grouping by that foreign key column leaves the projected key
 * ungrouped, and PostgreSQL rejects the statement: it resolves functional dependency syntactically and per table, so
 * the equality the join establishes does not carry it. These tests run the statements rather than only inspecting
 * them, so the rule is checked by the database.</p>
 *
 * @since 1.14
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)    // Prevent swapping to H2.
@DataJpaTest(showSql = false)
@Testcontainers
public class PostgreSQLGroupByForeignKeyPathTest {

    @SuppressWarnings("resource")
    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    public record City(
            @PK Integer id,
            String name
    ) implements Entity<Integer> {}

    public record Owner(
            @PK Integer id,
            String firstName,
            String lastName,
            @Nullable String telephone,
            @Nullable @FK City city
    ) implements Entity<Integer> {}

    public record Pet(
            @PK Integer id,
            String name,
            @Nullable LocalDate birthDate,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    private record OwnerPetCount(Owner owner, long petCount) {}

    private record OwnerIdCount(@Nullable Integer ownerId, long petCount) {}

    private static final Metamodel<Pet, Integer> OWNER_ID = Metamodel.of(Pet.class, "owner.id");
    private static final Metamodel<Pet, String> OWNER_FIRST_NAME = Metamodel.of(Pet.class, "owner.firstName");
    private static final Metamodel<Pet, String> OWNER_LAST_NAME = Metamodel.of(Pet.class, "owner.lastName");
    private static final Metamodel<Pet, String> OWNER_TELEPHONE = Metamodel.of(Pet.class, "owner.telephone");

    /**
     * The SELECT projects the owner, so the grouping has to name the owner's own key. PostgreSQL rejects the
     * statement outright when it names {@code pet.owner_id} instead, so reaching the assertions is the result.
     */
    @Test
    public void groupingByAProjectedOwnersKeyIsAcceptedByPostgreSQL() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String groupBy = sql.statement().substring(sql.statement().toUpperCase().indexOf("GROUP BY"));
            assertFalse(groupBy.contains("owner_id"),
                    "GROUP BY must name the owner's key, not the foreign key column: " + sql.statement());
        }, () -> {
            var counts = orm.selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                    .groupBy(OWNER_ID)
                    .getResultList();
            assertFalse(counts.isEmpty());
            assertTrue(counts.stream().allMatch(count -> count.petCount() > 0));
        });
    }

    /**
     * Ordering carries no such rule, so the foreign key column stays, sparing the join.
     */
    @Test
    public void orderingByTheOwnersKeyKeepsTheForeignKeyColumn() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String statement = sql.statement();
            if (statement.toUpperCase().contains("ORDER BY")) {
                String orderBy = statement.substring(statement.toUpperCase().indexOf("ORDER BY"));
                assertTrue(orderBy.contains("owner_id"),
                        "ORDER BY should keep naming the foreign key column: " + statement);
            }
        }, () -> assertFalse(orm.selectFrom(Pet.class).orderBy(OWNER_ID).getResultList().isEmpty()));
    }

    /**
     * Nothing projects the owner, so the foreign key column is grouped and the owner table is never joined.
     */
    @Test
    public void groupingWithoutProjectingTheOwnerKeepsTheForeignKeyColumn() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String statement = sql.statement();
            // The SELECT projects the foreign key column, so the grouping has to name that same column.
            assertTrue(statement.substring(statement.toUpperCase().indexOf("GROUP BY")).contains("owner_id"),
                    "GROUP BY must name the column the SELECT projects: " + statement);
        }, () -> {
            var counts = orm.selectFrom(Pet.class, OwnerIdCount.class, raw("\0, COUNT(*)", OWNER_ID))
                    .groupBy(OWNER_ID)
                    .getResultList();
            assertFalse(counts.isEmpty());
        });
    }

    /**
     * The selected graph spans two tables: grouping the owner determines the city, because a foreign key is to-one,
     * but PostgreSQL resolves functional dependency per table and will not carry it across the join. The grouping
     * has to name the city's key too, and naming it is the generator's job rather than the caller's.
     */
    @Test
    public void groupingAnOwnerCoversTheCityItDetermines() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String groupBy = sql.statement().substring(sql.statement().toUpperCase().indexOf("GROUP BY"));
            assertEquals(2, groupBy.split(",").length,
                    "The grouping must carry the keys of both selected tables: " + sql.statement());
        }, () -> {
            var counts = orm.selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                    .groupBy(OWNER_ID)
                    .getResultList();
            assertFalse(counts.isEmpty());
        });
    }

    /**
     * Grouping by every projected column of the referenced table is legal everywhere and must keep working.
     */
    @Test
    public void groupingByEveryProjectedColumnRemainsAccepted() {
        var orm = ORMTemplate.of(dataSource);
        var counts = orm.selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                .groupBy(OWNER_ID, OWNER_FIRST_NAME, OWNER_LAST_NAME, OWNER_TELEPHONE)
                .getResultList();
        assertFalse(counts.isEmpty());
        assertEquals(counts.size(), counts.stream().map(count -> count.owner().id()).distinct().count());
    }
}
