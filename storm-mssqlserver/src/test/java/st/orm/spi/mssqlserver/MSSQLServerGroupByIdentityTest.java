package st.orm.spi.mssqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;

import java.time.LocalDate;
import java.util.List;
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
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.core.template.Column;
import st.orm.core.template.ORMTemplate;

/**
 * SQL Server does not resolve functional dependency from a grouped key, so an identity grouping has to name every
 * column of the select list rather than the key alone. The caller writes the same query as on any other database;
 * the dialect decides what it takes to express it here.
 *
 * <p>H2 and PostgreSQL accept the key alone, so nothing in the main suite exercises this expansion. These tests do,
 * against a real SQL Server.</p>
 *
 * @since 1.14
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(showSql = false)
@Testcontainers
public class MSSQLServerGroupByIdentityTest {

    @SuppressWarnings("resource")
    @Container
    public static MSSQLServerContainer<?> sqlServerContainer =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2019-latest")
                    .acceptLicense()
                    .withPassword("test@1234")
                    .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", sqlServerContainer::getJdbcUrl);
        registry.add("spring.datasource.username", sqlServerContainer::getUsername);
        registry.add("spring.datasource.password", sqlServerContainer::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    public record Owner(
            @PK Integer id,
            String firstName,
            String lastName,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    public record Pet(
            @PK Integer id,
            String name,
            @Nullable LocalDate birthDate,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    private record OwnerPetCount(Owner owner, long petCount) {}

    private static final Metamodel<Pet, Integer> OWNER_ID = Metamodel.of(Pet.class, "owner.id");

    /**
     * The grouping the caller wrote names one owner. SQL Server rejects the statement unless every selected column
     * of that owner is grouped, so reaching the assertions is the result.
     */
    @Test
    public void anIdentityGroupingExpandsToTheSelectedColumns() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String statement = sql.statement();
            String groupBy = statement.substring(statement.toUpperCase().indexOf("GROUP BY"));
            assertTrue(groupBy.split(",").length >= 4,
                    "SQL Server needs every selected column of the owner grouped: " + statement);
            assertTrue(groupBy.contains("first_name"),
                    "The expansion must carry the owner's selected columns: " + statement);
        }, () -> {
            var counts = orm.selectFrom(Pet.class, OwnerPetCount.class, raw("\0, COUNT(*)", Owner.class))
                    .groupBy(OWNER_ID)
                    .getResultList();
            assertFalse(counts.isEmpty());
            assertTrue(counts.stream().allMatch(count -> count.petCount() > 0));
        });
    }

    /**
     * The expansion is the dialect's alone: it swaps the key for the selected columns and nothing else.
     */
    @Test
    public void theDialectReturnsTheSelectedColumnsForAnIdentityGrouping() {
        var dialect = new MSSQLServerSqlDialect();
        List<Column> key = List.of();
        List<Column> selected = List.of();
        assertEquals(selected, dialect.groupBy(key, selected));
    }
}
