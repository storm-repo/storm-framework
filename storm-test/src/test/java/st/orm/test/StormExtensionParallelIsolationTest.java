package st.orm.test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Verifies that concurrently executing test classes each see their own {@link DataSource}. The extension stores the
 * {@link DataSource} in the class-level store; a root-level store would let one class's {@code beforeAll} overwrite
 * the entry while another class's tests are still running.
 */
class StormExtensionParallelIsolationTest {

    /**
     * Latched so that both classes' {@code beforeAll} callbacks have stored their {@link DataSource} before either
     * class runs a test; without it, the overwrite this test guards against would depend on scheduling.
     */
    static CountDownLatch bothClassesStarted;

    @Test
    void concurrentTestClassesShouldEachSeeTheirOwnDataSource() {
        bothClassesStarted = new CountDownLatch(2);
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(AlphaCase.class), selectClass(BetaCase.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "same_thread")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(2).succeeded(2));
    }

    static void awaitOtherClass() throws InterruptedException {
        bothClassesStarted.countDown();
        assertTrue(bothClassesStarted.await(30, SECONDS), "Expected both test classes to start concurrently.");
    }

    static void assertDatabaseName(DataSource dataSource, String name) throws Exception {
        try (var conn = dataSource.getConnection()) {
            var url = conn.getMetaData().getURL();
            assertTrue(url.contains(name), "Expected connection URL to contain '" + name + "' but got: " + url);
        }
    }

    @StormTest
    static class AlphaCase {

        @BeforeAll
        static void awaitBetaCase() throws InterruptedException {
            awaitOtherClass();
        }

        @Test
        void dataSourceShouldBelongToThisClass(DataSource dataSource) throws Exception {
            assertDatabaseName(dataSource, "AlphaCase");
        }
    }

    @StormTest
    static class BetaCase {

        @BeforeAll
        static void awaitAlphaCase() throws InterruptedException {
            awaitOtherClass();
        }

        @Test
        void dataSourceShouldBelongToThisClass(DataSource dataSource) throws Exception {
            assertDatabaseName(dataSource, "BetaCase");
        }
    }
}
