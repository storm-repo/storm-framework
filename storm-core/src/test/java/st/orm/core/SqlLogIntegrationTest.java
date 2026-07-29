package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.IN;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import st.orm.Ref;
import st.orm.core.model.City;
import st.orm.core.model.City_;
import st.orm.core.model.Pet;
import st.orm.core.model.PetOwnerRef;
import st.orm.core.model.PetOwnerRef_;
import st.orm.core.template.JpaTemplate;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlLog;
import st.orm.core.template.StatementOrigin;

/**
 * Verifies that a scope reports what a call cost the database: the statements it took whichever repository issued
 * them, how many of those resolved references, and which type carried the weight.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class SqlLogIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testAScopeCoversEveryStatementOfTheCall() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("call", () -> {
            // Two unrelated repositories: a scope covers the call, not one repository.
            orm.entity(City.class).getById(1);
            orm.entity(PetOwnerRef.class).select().getResultList();
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        assertEquals("call", summary.name());
        assertEquals(2, summary.statementCount());
        assertEquals(2, summary.byStatement().size(), summary.byStatement().toString());
    }

    @Test
    public void testAScopeCountsTheReferencesTheCallResolved() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("n-plus-one", () -> {
            var pets = orm.entity(PetOwnerRef.class).select().getResultList();
            pets.stream().map(PetOwnerRef::owner).filter(Objects::nonNull).forEach(Ref::fetch);
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        // One select, then one statement per reference it left unresolved.
        assertEquals(1, summary.count(StatementOrigin.DIRECT));
        assertTrue(summary.count(StatementOrigin.FETCH) > 0, summary.statements().toString());
        assertTrue(summary.byStatement().stream().anyMatch(line -> line.fetch()),
                summary.byStatement().toString());
    }

    @Test
    public void testAResolvingQueryLeavesTheScopeAtOneStatement() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("fetched", () -> {
            var pets = orm.entity(PetOwnerRef.class).select()
                    .fetch(PetOwnerRef_.owner)
                    .getResultList();
            pets.stream().map(PetOwnerRef::owner).filter(Objects::nonNull).forEach(Ref::fetch);
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        assertEquals(1, summary.statementCount());
        assertEquals(0, summary.count(StatementOrigin.FETCH));
    }

    @Test
    public void testAFailedCallIsStillSummarized() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        try {
            SqlLog.record("failing", () -> {
                orm.entity(City.class).getById(1);
                throw new IllegalStateException("boom");
            }, summaries::add);
        } catch (IllegalStateException ignore) {
            // The statements leading up to a failure are the evidence, so they are reported.
        }
        assertEquals(1, summaries.getFirst().statementCount());
    }

    @Test
    public void testStatementsBeyondTheLimitAreCountedAndReported() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("limited", 1, (Supplier<Object>) () -> {
            orm.entity(City.class).getById(1);
            orm.entity(City.class).getById(2);
            orm.entity(City.class).getById(3);
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        assertEquals(3, summary.statementCount(), "the count covers every statement");
        assertEquals(1, summary.statements().size(), "only the limit is retained");
        assertTrue(summary.truncated());
    }

    @Test
    public void testNoScopeLeaksAfterTheCall() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("scoped", (Supplier<Object>) () -> orm.entity(City.class).getById(1), summaries::add);
        int during = summaries.getFirst().statementCount();
        // A statement executed after the scope closed is not recorded into it.
        orm.entity(City.class).getById(2);
        assertEquals(during, summaries.getFirst().statementCount());
        assertFalse(summaries.getFirst().truncated());
    }

    @Test
    public void testCollectionSizesGroupAsOneStatement() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("in-lists", (Supplier<Object>) () -> {
            orm.entity(City.class).select().where(City_.id, IN, List.of(1)).getResultList();
            orm.entity(City.class).select().where(City_.id, IN, List.of(1, 2)).getResultList();
            orm.entity(City.class).select().where(City_.id, IN, List.of(1, 2, 3)).getResultList();
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        assertEquals(3, summary.statementCount());
        // The statements differ in placeholder count but share their template, so they group as one line.
        assertEquals(1, summary.byStatement().size(), summary.byStatement().toString());
        var line = summary.byStatement().getFirst();
        assertEquals(3, line.executions());
        assertEquals(3, line.variants());
    }

    @Test
    public void testStatementsCarryTheirDuration() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("timed", (Supplier<Object>) () -> orm.entity(City.class).getById(1), summaries::add);
        var summary = summaries.getFirst();
        assertTrue(summary.statements().getFirst().durationNanos() > 0, summary.statements().toString());
        assertTrue(summary.databaseNanos() > 0);
        assertEquals(1, summary.peakConcurrency());
    }

    @Test
    public void testAScopeOpensAndClosesWithTryWithResources() {
        var orm = ORMTemplate.of(dataSource);
        var scope = SqlLog.open("block");
        try (scope) {
            orm.entity(City.class).getById(1);
            orm.entity(City.class).getById(2);
        }
        var summary = scope.summary();
        assertEquals(2, summary.statementCount());
        // A statement executed after the scope closed is not recorded into it.
        orm.entity(City.class).getById(3);
        assertEquals(2, summary.statementCount());
    }

    @Test
    public void testCallSitesNameTheFrameThatCausedTheExecution() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.recordThrowing("sited", 200, true, () -> {
            orm.entity(City.class).getById(1);
            return null;
        }, summaries::add);
        var line = summaries.getFirst().byStatement().getFirst();
        // This test lives under st.orm, which the walker treats as framework, so the frame it finds is the
        // test runner's; an application's own frame is the first outside the framework packages.
        assertNotNull(line.callSite());
        assertTrue(line.callSite().matches(".+:\\d+"), line.callSite());
        assertEquals(1, line.sites());
    }

    @Test
    public void testWithoutCallSitesNoStackIsWalked() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("unsited", (Supplier<Object>) () -> orm.entity(City.class).getById(1), summaries::add);
        assertNull(summaries.getFirst().byStatement().getFirst().callSite());
    }

    @Test
    public void testTheDetailedRenderingCarriesTheFullStatement() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("detailed", (Supplier<Object>) () -> orm.entity(City.class).getById(1), summaries::add);
        String detailed = summaries.getFirst().toDetailedString();
        assertTrue(detailed.contains("statements:"), detailed);
        // The full text follows the summary, un-elided.
        assertTrue(detailed.contains("SELECT c.id, c.name FROM city c WHERE c.id = ?"), detailed);
    }

    @Test
    public void testStatementsCarryTheRowsTheyProduced() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("rows", (Supplier<Object>) () -> {
            orm.entity(City.class).getById(1);
            return orm.entity(City.class).select().getResultList();
        }, summaries::add);
        var summary = summaries.getFirst();
        var byRows = summary.byStatement();
        // The lookup produced one row; the unfiltered select produced as many as the list holds.
        var lookup = byRows.stream().filter(line -> line.statement().contains("WHERE")).findFirst().orElseThrow();
        assertEquals(1, lookup.rows());
        var all = byRows.stream().filter(line -> !line.statement().contains("WHERE")).findFirst().orElseThrow();
        assertTrue(all.rows() > 1, String.valueOf(all.rows()));
        // A fully consumed result is an exact count.
        assertTrue(all.exactRows());
    }

    @Test
    public void testAnEarlyClosedStreamReportsItsCountAsALowerBound() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("partial", (Supplier<Object>) () -> {
            try (var stream = orm.entity(City.class).select().getResultStream()) {
                return stream.findFirst().orElseThrow();
            }
        }, summaries::add);
        var line = summaries.getFirst().byStatement().getFirst();
        // One row was consumed before the stream closed; more may have existed, so the count is a known lower
        // bound and the rendering marks it.
        assertEquals(1, line.rows());
        assertFalse(line.exactRows());
        assertTrue(summaries.getFirst().toString().contains("1* rows"), summaries.getFirst().toString());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    @Test
    public void testCacheServedReadsAreCounted() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("cached", (Supplier<Object>) () -> {
            var cities = orm.entity(City.class);
            cities.getById(1);
            // The second read is served by the transaction's entity cache: no statement, one hit.
            cities.getById(1);
            return null;
        }, summaries::add);
        var summary = summaries.getFirst();
        assertEquals(1, summary.statementCount(), summary.toString());
        assertEquals(1, summary.cacheHits());
        assertTrue(summary.toString().contains("1 from cache"), summary.toString());
    }

    @Test
    public void testAScopeCoversTheJpaTemplatePath() {
        var orm = JpaTemplate.ORM(entityManager);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("jpa", (Supplier<Object>) () ->
                orm.query("SELECT id, name FROM city WHERE id = 1").getResultList(), summaries::add);
        var summary = summaries.getFirst();
        // The JPA template path brackets its executions like the JDBC path: the statement, its duration, and
        // the rows it produced all reach the scope.
        assertEquals(1, summary.statementCount(), summary.toString());
        var line = summary.byStatement().getFirst();
        assertEquals(1, line.rows());
        assertTrue(line.durationNanos() > 0);
        assertTrue(summary.toString().contains("FROM city"), summary.toString());
    }

    @Test
    public void testHydrationShapesAreOffByDefault() {
        var orm = ORMTemplate.of(dataSource);
        List<SqlLog.Summary> summaries = new ArrayList<>();
        SqlLog.record("off", (Supplier<Object>) () -> orm.entity(Pet.class).getById(1), summaries::add);
        var summary = summaries.getFirst();
        // Shapes render only when switched on, in either rendering.
        assertFalse(summary.toString().contains("j2"), summary.toString());
        assertFalse(summary.toDetailedString().contains("graph="), summary.toDetailedString());
    }

    @Test
    public void testShortShapesSkipFlatTypes() {
        var orm = ORMTemplate.of(dataSource);
        SqlLog.hydrationShapes(SqlLog.HydrationShapes.SHORT);
        try {
            List<SqlLog.Summary> summaries = new ArrayList<>();
            SqlLog.record("short", (Supplier<Object>) () -> orm.entity(City.class).getById(1), summaries::add);
            // A flat type says nothing its row does not already say, so the short form stays off its row.
            assertFalse(summaries.getFirst().toString().contains("j0"), summaries.getFirst().toString());
        } finally {
            SqlLog.hydrationShapes(SqlLog.HydrationShapes.OFF);
        }
    }

    @Test
    public void testTheConfiguredFormRendersTheHydrationShape() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        // Pet's type is a reference, so it stays a foreign key column; Owner reaches City through an inline
        // Address, which is columns on the owner table rather than a subgraph, so City splices into Owner's
        // children. The short form states the numbers only; the full form names the graph.
        SqlLog.hydrationShapes(SqlLog.HydrationShapes.SHORT);
        try {
            List<SqlLog.Summary> summaries = new ArrayList<>();
            SqlLog.record("shape", (Supplier<Object>) () -> orm.entity(Pet.class).getById(1), summaries::add);
            assertTrue(summaries.getFirst().toString().contains("j2 c12 d3"), summaries.getFirst().toString());
            assertFalse(summaries.getFirst().toString().contains("graph="), summaries.getFirst().toString());
            SqlLog.hydrationShapes(SqlLog.HydrationShapes.FULL);
            summaries.clear();
            SqlLog.record("shape", (Supplier<Object>) () -> orm.entity(Pet.class).getById(1), summaries::add);
            assertTrue(summaries.getFirst().toString().contains("joins=2 columns=12 graph=Pet(Owner(City))"),
                    summaries.getFirst().toString());
        } finally {
            SqlLog.hydrationShapes(SqlLog.HydrationShapes.OFF);
        }
    }

    @Test
    public void testAReportedSummaryLogsUnderTheScopeLogger() {
        var orm = ORMTemplate.of(dataSource);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("st.orm.sql.perf");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var level = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            List<SqlLog.Summary> summaries = new ArrayList<>();
            SqlLog.record("reported", (Supplier<Object>) () -> orm.entity(City.class).getById(1),
                    summaries::add);
            SqlLog.report(summaries.getFirst());
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().startsWith("SQL (reported):"),
                    appender.list.getFirst().getFormattedMessage());
        } finally {
            logger.setLevel(level);
            logger.detachAppender(appender);
        }
    }

    @Test
    public void testASummaryWithoutStatementsIsNotReported() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("st.orm.sql.perf");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var level = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            List<SqlLog.Summary> summaries = new ArrayList<>();
            SqlLog.record("silent", (Supplier<Object>) () -> null, summaries::add);
            SqlLog.report(summaries.getFirst());
            assertTrue(appender.list.isEmpty());
        } finally {
            logger.setLevel(level);
            logger.detachAppender(appender);
        }
    }
}
