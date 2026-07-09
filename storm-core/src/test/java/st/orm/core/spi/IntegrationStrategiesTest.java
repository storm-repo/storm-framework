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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.core.spi.QueryContext.ExecutionKind;
import st.orm.core.spi.QueryObserver.Observation;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlOperation;
import st.orm.core.testsupport.TestSpringConnectionProvider;

/**
 * Tests for the instance-scoped integration strategies configured through {@link ORMTemplate.Builder}: strategy
 * precedence over {@code ServiceLoader} discovery, the {@link ExceptionMapper} contract, and the
 * {@link QueryObserver} lifecycle.
 */
public class IntegrationStrategiesTest {

    private static DataSource dataSource;

    @BeforeAll
    static void setUp() throws SQLException {
        var h2DataSource = org.springframework.boot.jdbc.DataSourceBuilder.create()
                .url("jdbc:h2:mem:integrationStrategies;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        try (var connection = h2DataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS strategy_city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
            statement.execute("DELETE FROM strategy_city");
            statement.execute("INSERT INTO strategy_city (name) VALUES ('Amsterdam'), ('Utrecht')");
        }
        dataSource = h2DataSource;
    }

    static class CountingConnectionProvider implements ConnectionProvider {
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicInteger released = new AtomicInteger();

        @Override
        public Connection getConnection(@Nonnull DataSource dataSource, @Nullable TransactionContext context) {
            acquired.incrementAndGet();
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
        }

        @Override
        public void releaseConnection(@Nonnull Connection connection, @Nonnull DataSource dataSource,
                                      @Nullable TransactionContext context) {
            released.incrementAndGet();
            try {
                connection.close();
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
        }
    }

    record ObservedExecution(SqlOperation operation, ExecutionKind kind, @Nullable String statement,
                             List<Throwable> errors, AtomicInteger closed) {
    }

    static class RecordingObserver implements QueryObserver {
        final List<ObservedExecution> executions = new ArrayList<>();

        @Override
        public Observation onExecute(@Nonnull QueryContext context) {
            var execution = new ObservedExecution(context.operation(), context.kind(),
                    context.statement().orElse(null), new ArrayList<>(), new AtomicInteger());
            executions.add(execution);
            return new Observation() {
                @Override
                public void error(@Nonnull Throwable throwable) {
                    execution.errors().add(throwable);
                }

                @Override
                public void close() {
                    execution.closed().incrementAndGet();
                }
            };
        }
    }

    static class MappedException extends RuntimeException {
        MappedException(Throwable cause) {
            super(cause);
        }
    }

    @Test
    public void explicitConnectionProviderTakesPrecedenceOverServiceLoader() {
        var connectionProvider = new CountingConnectionProvider();
        var orm = ORMTemplate.builder(dataSource)
                .connectionProvider(connectionProvider)
                .build();
        var rows = orm.query(raw("SELECT id, name FROM strategy_city")).getResultList();
        assertTrue(rows.size() >= 2);
        assertTrue(connectionProvider.acquired.get() > 0);
        assertEquals(connectionProvider.acquired.get(), connectionProvider.released.get());
    }

    @Test
    public void serviceLoaderFallbackResolvesOrderedWinner() {
        // The test classpath registers a @BeforeAny provider next to the @AfterAny core default; the fallback
        // resolution must pick the explicitly ordered winner instead of relying on classpath order.
        assertEquals(TestSpringConnectionProvider.class, Providers.getConnectionProvider().getClass());
    }

    @Test
    public void connectionProviderRejectedForConnectionBackedTemplate() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            var builder = ORMTemplate.builder(connection)
                    .connectionProvider(new CountingConnectionProvider());
            assertThrows(PersistenceException.class, builder::build);
        }
    }

    @Test
    public void exceptionMapperReceivesContextAndMapsFailures() {
        var contexts = new ArrayList<ExceptionContext>();
        var orm = ORMTemplate.builder(dataSource)
                .exceptionMapper((cause, context) -> {
                    contexts.add(context);
                    return new MappedException(cause);
                })
                .build();
        assertThrows(MappedException.class, () -> orm.query(raw("SELECT * FROM does_not_exist")).getResultList());
        assertEquals(1, contexts.size());
        var context = contexts.getFirst();
        assertEquals(SqlOperation.SELECT, context.operation());
        assertTrue(context.statement().isPresent());
        assertTrue(context.statement().get().contains("does_not_exist"));
    }

    @Test
    public void exceptionMapperFailureNeverMasksOriginalError() {
        var mapperFailure = new IllegalStateException("mapper blew up");
        var orm = ORMTemplate.builder(dataSource)
                .exceptionMapper((cause, context) -> {
                    throw mapperFailure;
                })
                .build();
        var thrown = assertThrows(PersistenceException.class,
                () -> orm.query(raw("SELECT * FROM does_not_exist")).getResultList());
        assertTrue(Arrays.asList(thrown.getSuppressed()).contains(mapperFailure),
                "The mapper failure must be suppressed onto the original error.");
    }

    @Test
    public void queryObserverObservesQueryUntilStreamCompletion() {
        var observer = new RecordingObserver();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(observer)
                .build();
        var rows = orm.query(raw("SELECT id, name FROM strategy_city")).getResultList();
        assertTrue(rows.size() >= 2);
        assertEquals(1, observer.executions.size());
        var execution = observer.executions.getFirst();
        assertEquals(ExecutionKind.QUERY, execution.kind());
        assertEquals(SqlOperation.SELECT, execution.operation());
        assertNotNull(execution.statement());
        assertTrue(execution.errors().isEmpty());
        assertEquals(1, execution.closed().get(), "The observation must be closed exactly once.");
    }

    @Test
    public void queryObserverObservesUpdate() {
        var observer = new RecordingObserver();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(observer)
                .build();
        int affected = orm.query(raw("INSERT INTO strategy_city (name) VALUES ('Eindhoven')")).executeUpdate();
        assertEquals(1, affected);
        assertEquals(1, observer.executions.size());
        var execution = observer.executions.getFirst();
        assertEquals(ExecutionKind.UPDATE, execution.kind());
        assertTrue(execution.errors().isEmpty());
        assertEquals(1, execution.closed().get());
    }

    @Test
    public void queryObserverReceivesErrorAndCloseOnFailure() {
        var observer = new RecordingObserver();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(observer)
                .build();
        assertThrows(PersistenceException.class, () -> orm.query(raw("SELECT * FROM does_not_exist")).getResultList());
        assertEquals(1, observer.executions.size());
        var execution = observer.executions.getFirst();
        assertFalse(execution.errors().isEmpty(), "The observation must be signaled of the failure.");
        assertEquals(1, execution.closed().get(), "The observation must be closed exactly once.");
    }

    @Test
    public void queryObserverFailuresNeverAffectQueryExecution() {
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(context -> {
                    throw new IllegalStateException("observer blew up");
                })
                .build();
        var rows = orm.query(raw("SELECT id, name FROM strategy_city")).getResultList();
        assertFalse(rows.isEmpty());
    }

    @Test
    public void observationFailuresAreContained() {
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(context -> new Observation() {
                    @Override
                    public void error(@Nonnull Throwable throwable) {
                        throw new IllegalStateException("error signal blew up");
                    }

                    @Override
                    public void close() {
                        throw new IllegalStateException("close signal blew up");
                    }
                })
                .build();
        var rows = orm.query(raw("SELECT id, name FROM strategy_city")).getResultList();
        assertFalse(rows.isEmpty());
        assertInstanceOf(PersistenceException.class,
                assertThrows(RuntimeException.class,
                        () -> orm.query(raw("SELECT * FROM does_not_exist")).getResultList()));
    }
}
