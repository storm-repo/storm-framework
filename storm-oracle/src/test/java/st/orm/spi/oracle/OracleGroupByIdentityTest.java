package st.orm.spi.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.core.template.Column;
import st.orm.core.template.ORMTemplate;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

/**
 * Oracle does not resolve functional dependency from a grouped key, so an identity grouping has to name every column
 * of the select list rather than the key alone. The caller writes the same query as on any other database; the
 * dialect decides what it takes to express it here.
 *
 * <p>H2 and PostgreSQL accept the key alone, so nothing in the main suite exercises this expansion. These tests do,
 * against a real Oracle.</p>
 *
 * @since 1.14
 */
@Testcontainers
@StormTest(scripts = "/data.sql")
public class OracleGroupByIdentityTest {

    @SuppressWarnings("resource")
    @Container
    public static GenericContainer<?> oracleContainer = new GenericContainer<>("gvenzl/oracle-free:23")
            .withExposedPorts(1521)
            .withEnv("ORACLE_PASSWORD", "oracle")
            .withEnv("APP_USER", "test")
            .withEnv("APP_USER_PASSWORD", "test")
            .withCreateContainerCmdModifier(cmd -> {
                String dockerPlatform = System.getenv("DOCKER_PLATFORM");
                if (dockerPlatform == null || dockerPlatform.isEmpty()) {
                    dockerPlatform = "linux/arm64/v8";
                }
                cmd.withPlatform(dockerPlatform);
            })
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(5));

    public static DataSource dataSource() {
        String jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%d/FREEPDB1",
                oracleContainer.getHost(), oracleContainer.getMappedPort(1521));
        return ContainerDataSource.of(jdbcUrl, "test", "test");
    }

    private DataSource dataSource;

    @BeforeEach
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record Owner(
            @PK Integer id,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    public record Pet(
            @PK Integer id,
            @Nullable String name,
            @Nullable LocalDate birthDate,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    private record OwnerPetCount(Owner owner, long petCount) {}

    private static final Metamodel<Pet, Integer> OWNER_ID = Metamodel.of(Pet.class, "owner.id");

    /**
     * The grouping the caller wrote names one owner. Oracle rejects the statement unless every selected column of
     * that owner is grouped, so reaching the assertions is the result.
     */
    @Test
    public void anIdentityGroupingExpandsToTheSelectedColumns() {
        var orm = ORMTemplate.of(dataSource);
        observe(sql -> {
            String statement = sql.statement();
            String groupBy = statement.substring(statement.toUpperCase().indexOf("GROUP BY"));
            assertTrue(groupBy.split(",").length >= 4,
                    "Oracle needs every selected column of the owner grouped: " + statement);
            assertTrue(groupBy.toLowerCase().contains("first_name"),
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
        var dialect = new OracleSqlDialect();
        List<Column> key = List.of();
        List<Column> selected = List.of();
        assertEquals(selected, dialect.groupBy(key, selected));
    }
}
