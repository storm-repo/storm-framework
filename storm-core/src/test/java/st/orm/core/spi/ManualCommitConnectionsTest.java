/*
 * Copyright 2024 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import st.orm.PersistenceException;
import st.orm.TransactionPropagation;
import st.orm.core.template.ORMTemplate;

/**
 * Tests for the manual-commit connection declaration: verified arrival state in both directions, flip-free
 * transactional handling on declared pools, and explicit commit handling on the non-transactional paths.
 */
public class ManualCommitConnectionsTest {

    private static DataSource h2DataSource;

    @BeforeAll
    static void setUp() throws SQLException {
        h2DataSource = org.springframework.boot.jdbc.DataSourceBuilder.create()
                .url("jdbc:h2:mem:manualCommitConnections;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        try (var connection = h2DataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS manual_city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
        }
    }

    @BeforeEach
    void resetTable() throws SQLException {
        try (var connection = h2DataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DELETE FROM manual_city");
        }
    }

    /**
     * A pool that hands out connections in a configurable auto-commit state, tracking the auto-commit flips
     * Storm performs after handout and the auto-commit state each connection is released in.
     */
    static final class TrackingDataSource extends DelegatingDataSource {
        final boolean manualCommitHandout;
        final AtomicInteger autoCommitFlips = new AtomicInteger();
        final List<Boolean> releasedAutoCommitStates = new ArrayList<>();

        TrackingDataSource(DataSource target, boolean manualCommitHandout) {
            super(target);
            this.manualCommitHandout = manualCommitHandout;
        }

        void resetTracking() {
            autoCommitFlips.set(0);
            releasedAutoCommitStates.clear();
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            // The handout flip below happens on the raw connection, so the proxy counts only the flips
            // performed after handout.
            if (manualCommitHandout) {
                connection.setAutoCommit(false);
            }
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("setAutoCommit")) {
                            autoCommitFlips.incrementAndGet();
                        }
                        if (method.getName().equals("close")) {
                            releasedAutoCommitStates.add(connection.getAutoCommit());
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    });
        }
    }

    private static ORMTemplate declaredTemplate(TrackingDataSource dataSource) {
        var template = ORMTemplate.builder(dataSource)
                .connectionProvider(new JdbcConnectionProviderImpl(true))
                .transactionTemplateProvider(new JdbcTransactionTemplateProviderImpl())
                .build();
        dataSource.resetTracking(); // Building the template probes a connection for dialect detection.
        return template;
    }

    private static ORMTemplate undeclaredTemplate(TrackingDataSource dataSource) {
        var template = ORMTemplate.builder(dataSource)
                .connectionProvider(new JdbcConnectionProviderImpl())
                .transactionTemplateProvider(new JdbcTransactionTemplateProviderImpl())
                .build();
        dataSource.resetTracking();
        return template;
    }

    private static <R> R inTransaction(TransactionRunner.Block<R, RuntimeException> block) {
        return inTransaction(null, block);
    }

    private static <R> R inTransaction(TransactionPropagation propagation,
                                       TransactionRunner.Block<R, RuntimeException> block) {
        return TransactionRunner.execute(
                new TransactionScope.Options(propagation, null, null, null, false), block);
    }

    private static int countRows() throws SQLException {
        try (var connection = h2DataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM manual_city")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static void assertMessageChainContains(Throwable thrown, String text) {
        for (var current = thrown; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return;
            }
        }
        throw new AssertionError("Expected message containing '" + text + "', got: " + thrown);
    }

    @Test
    public void transactionCommitsWithoutFlipsOnDeclaredManualCommitPool() throws SQLException {
        var pool = new TrackingDataSource(h2DataSource, true);
        var orm = declaredTemplate(pool);
        inTransaction(ignore -> orm.query(raw("INSERT INTO manual_city (name) VALUES ('Amsterdam')")).executeUpdate());
        assertEquals(1, countRows());
        assertEquals(0, pool.autoCommitFlips.get(),
                "The declared transactional path must not flip auto-commit.");
        assertEquals(List.of(false), pool.releasedAutoCommitStates,
                "The connection must be released in its arrived manual-commit state.");
    }

    @Test
    public void transactionRollsBackOnDeclaredManualCommitPool() throws SQLException {
        var pool = new TrackingDataSource(h2DataSource, true);
        var orm = declaredTemplate(pool);
        assertThrows(IllegalStateException.class, () -> inTransaction(ignore -> {
            orm.query(raw("INSERT INTO manual_city (name) VALUES ('Utrecht')")).executeUpdate();
            throw new IllegalStateException("fail the block");
        }));
        assertEquals(0, countRows(), "The failed block must be rolled back, not committed per statement.");
        assertEquals(0, pool.autoCommitFlips.get());
        assertEquals(List.of(false), pool.releasedAutoCommitStates);
    }

    @Test
    public void declaredTemplateFailsFastOnAutoCommitPool() {
        var pool = new TrackingDataSource(h2DataSource, false);
        var orm = declaredTemplate(pool);
        var transactional = assertThrows(PersistenceException.class,
                () -> inTransaction(ignore -> orm.query(raw("SELECT id FROM manual_city")).getResultList()));
        assertMessageChainContains(transactional, "declares manual-commit connections");
        var nonTransactional = assertThrows(PersistenceException.class,
                () -> orm.query(raw("SELECT id FROM manual_city")).getResultList());
        assertMessageChainContains(nonTransactional, "declares manual-commit connections");
    }

    @Test
    public void undeclaredTemplateFailsFastOnManualCommitPool() throws SQLException {
        var pool = new TrackingDataSource(h2DataSource, true);
        var orm = undeclaredTemplate(pool);
        var transactional = assertThrows(PersistenceException.class,
                () -> inTransaction(ignore -> orm.query(raw("SELECT id FROM manual_city")).getResultList()));
        assertMessageChainContains(transactional, "manualCommitConnections()");
        var nonTransactional = assertThrows(PersistenceException.class,
                () -> orm.query(raw("INSERT INTO manual_city (name) VALUES ('Rotterdam')")).executeUpdate());
        assertMessageChainContains(nonTransactional, "manualCommitConnections()");
        assertEquals(0, countRows(), "No statement may execute on an unverified manual-commit connection.");
    }

    @Test
    public void nonTransactionalWriteCommitsOnDeclaredManualCommitPool() throws SQLException {
        var pool = new TrackingDataSource(h2DataSource, true);
        var orm = declaredTemplate(pool);
        orm.query(raw("INSERT INTO manual_city (name) VALUES ('Eindhoven')")).executeUpdate();
        assertEquals(1, countRows(),
                "A non-transactional write must commit; the pool would roll back an uncommitted statement.");
        assertEquals(List.of(false), pool.releasedAutoCommitStates,
                "The connection must be restored to its arrived manual-commit state before release.");
    }

    @Test
    public void notSupportedScopeCommitsImmediatelyOnDeclaredManualCommitPool() throws SQLException {
        var pool = new TrackingDataSource(h2DataSource, true);
        var orm = declaredTemplate(pool);
        assertThrows(IllegalStateException.class, () -> inTransaction(ignore -> {
            orm.query(raw("INSERT INTO manual_city (name) VALUES ('inside-transaction')")).executeUpdate();
            inTransaction(TransactionPropagation.NOT_SUPPORTED, nested ->
                    orm.query(raw("INSERT INTO manual_city (name) VALUES ('outside-transaction')")).executeUpdate());
            throw new IllegalStateException("fail the outer block");
        }));
        try (var connection = h2DataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT name FROM manual_city")) {
            assertTrue(resultSet.next());
            assertEquals("outside-transaction", resultSet.getString(1),
                    "The NOT_SUPPORTED write must survive the outer rollback.");
            assertFalse(resultSet.next(), "The transactional write must be rolled back.");
        }
    }

    @Test
    public void declarationRejectedWithCustomConnectionProvider() {
        var builder = ORMTemplate.builder(h2DataSource)
                .connectionProvider(new JdbcConnectionProviderImpl())
                .manualCommitConnections();
        var thrown = assertThrows(PersistenceException.class, builder::build);
        assertMessageChainContains(thrown, "cannot be combined with a custom connection provider");
    }

    @Test
    public void declarationRejectedForConnectionBackedTemplate() throws SQLException {
        try (var connection = h2DataSource.getConnection()) {
            var builder = ORMTemplate.builder(connection).manualCommitConnections();
            var thrown = assertThrows(PersistenceException.class, builder::build);
            assertMessageChainContains(thrown, "connection backed template");
        }
    }

    @Test
    public void declarationRejectedWhenDiscoveryResolvesPlatformProvider() {
        // The test classpath registers a @BeforeAny platform provider that wins discovery; the declaration only
        // governs the built-in JDBC provider and must fail fast rather than silently replace the platform's
        // connection handling.
        var builder = ORMTemplate.builder(h2DataSource).manualCommitConnections();
        var thrown = assertThrows(PersistenceException.class, builder::build);
        assertMessageChainContains(thrown, "TestSpringConnectionProvider");
    }
}
