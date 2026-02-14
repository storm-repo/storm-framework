package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.City;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlInterceptor;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class SqlInterceptorIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // -----------------------------------------------------------------------
    // 1. observe(Consumer<Sql>, Supplier<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testObserveWithSupplier() {
        List<String> observedSql = new ArrayList<>();
        long count = SqlInterceptor.observe(
                sql -> observedSql.add(sql.statement()),
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(observedSql.isEmpty());
        assertTrue(observedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 2. observeThrowing(Consumer<Sql>, Callable<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testObserveThrowingWithCallable() throws Exception {
        List<String> observedSql = new ArrayList<>();
        long count = SqlInterceptor.observeThrowing(
                sql -> observedSql.add(sql.statement()),
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(observedSql.isEmpty());
        assertTrue(observedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    @Test
    public void testObserveThrowingCallablePropagatesException() {
        List<String> observedSql = new ArrayList<>();
        Exception thrown = assertThrows(Exception.class, () ->
                SqlInterceptor.observeThrowing(
                        sql -> observedSql.add(sql.statement()),
                        () -> { throw new Exception("test exception"); }
                )
        );
        assertEquals("test exception", thrown.getMessage());
    }

    // -----------------------------------------------------------------------
    // 3. observe(UnaryOperator<SqlTemplate>, Consumer<Sql>, Runnable)
    // -----------------------------------------------------------------------

    @Test
    public void testObserveWithCustomizerAndRunnable() {
        List<String> observedSql = new ArrayList<>();
        SqlInterceptor.observe(
                t -> t,  // identity customizer
                sql -> observedSql.add(sql.statement()),
                () -> {
                    var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
                    assertEquals(6, cities.size());
                }
        );
        assertFalse(observedSql.isEmpty());
        assertTrue(observedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 4. observe(UnaryOperator<SqlTemplate>, Consumer<Sql>, Supplier<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testObserveWithCustomizerAndSupplier() {
        List<String> observedSql = new ArrayList<>();
        long count = SqlInterceptor.observe(
                t -> t,  // identity customizer
                sql -> observedSql.add(sql.statement()),
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(observedSql.isEmpty());
        assertTrue(observedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 5. observeThrowing(UnaryOperator<SqlTemplate>, Consumer<Sql>, Callable<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testObserveThrowingWithCustomizerAndCallable() throws Exception {
        List<String> observedSql = new ArrayList<>();
        long count = SqlInterceptor.observeThrowing(
                t -> t,  // identity customizer
                sql -> observedSql.add(sql.statement()),
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(observedSql.isEmpty());
        assertTrue(observedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    @Test
    public void testObserveThrowingWithCustomizerPropagatesException() {
        List<String> observedSql = new ArrayList<>();
        Exception thrown = assertThrows(Exception.class, () ->
                SqlInterceptor.observeThrowing(
                        t -> t,
                        sql -> observedSql.add(sql.statement()),
                        () -> { throw new Exception("customizer exception"); }
                )
        );
        assertEquals("customizer exception", thrown.getMessage());
    }

    // -----------------------------------------------------------------------
    // 6. intercept(UnaryOperator<Sql>, Runnable)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptWithRunnable() {
        List<String> interceptedSql = new ArrayList<>();
        SqlInterceptor.intercept(
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;  // pass through unchanged
                },
                () -> {
                    var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
                    assertEquals(6, cities.size());
                }
        );
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 7. intercept(UnaryOperator<Sql>, Supplier<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptWithSupplier() {
        List<String> interceptedSql = new ArrayList<>();
        long count = SqlInterceptor.intercept(
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;  // pass through unchanged
                },
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 8. interceptThrowing(UnaryOperator<Sql>, Callable<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptThrowingWithCallable() throws Exception {
        List<String> interceptedSql = new ArrayList<>();
        long count = SqlInterceptor.interceptThrowing(
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;
                },
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    @Test
    public void testInterceptThrowingCallablePropagatesException() {
        List<String> interceptedSql = new ArrayList<>();
        Exception thrown = assertThrows(Exception.class, () ->
                SqlInterceptor.interceptThrowing(
                        sql -> {
                            interceptedSql.add(sql.statement());
                            return sql;
                        },
                        () -> { throw new Exception("intercept exception"); }
                )
        );
        assertEquals("intercept exception", thrown.getMessage());
    }

    // -----------------------------------------------------------------------
    // 9. intercept(UnaryOperator<SqlTemplate>, UnaryOperator<Sql>, Runnable)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptWithCustomizerAndRunnable() {
        List<String> interceptedSql = new ArrayList<>();
        SqlInterceptor.intercept(
                t -> t,  // identity customizer
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;
                },
                () -> {
                    var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
                    assertEquals(6, cities.size());
                }
        );
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 10. intercept(UnaryOperator<SqlTemplate>, UnaryOperator<Sql>, Supplier<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptWithCustomizerAndSupplier() {
        List<String> interceptedSql = new ArrayList<>();
        long count = SqlInterceptor.intercept(
                t -> t,  // identity customizer
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;
                },
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    // -----------------------------------------------------------------------
    // 11. interceptThrowing(UnaryOperator<SqlTemplate>, UnaryOperator<Sql>, Callable<T>)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptThrowingWithCustomizerAndCallable() throws Exception {
        List<String> interceptedSql = new ArrayList<>();
        long count = SqlInterceptor.interceptThrowing(
                t -> t,  // identity customizer
                sql -> {
                    interceptedSql.add(sql.statement());
                    return sql;
                },
                () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
        );
        assertEquals(6, count);
        assertFalse(interceptedSql.isEmpty());
        assertTrue(interceptedSql.stream().anyMatch(s -> s.toUpperCase().contains("SELECT")));
    }

    @Test
    public void testInterceptThrowingWithCustomizerPropagatesException() {
        List<String> interceptedSql = new ArrayList<>();
        Exception thrown = assertThrows(Exception.class, () ->
                SqlInterceptor.interceptThrowing(
                        t -> t,
                        sql -> {
                            interceptedSql.add(sql.statement());
                            return sql;
                        },
                        () -> { throw new Exception("customizer intercept exception"); }
                )
        );
        assertEquals("customizer intercept exception", thrown.getMessage());
    }

    // -----------------------------------------------------------------------
    // 12. registerGlobalObserver / unregisterGlobalObserver
    // -----------------------------------------------------------------------

    @Test
    public void testGlobalObserverLifecycle() {
        List<String> observedSql = new ArrayList<>();
        Consumer<Sql> observer = sql -> observedSql.add(sql.statement());

        SqlInterceptor.registerGlobalObserver(observer);
        try {
            // First query: observer is active.
            var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
            assertEquals(6, cities.size());
            int countAfterFirst = observedSql.size();
            assertTrue(countAfterFirst > 0, "Global observer should have captured SQL during first query.");

            // Unregister the observer.
            SqlInterceptor.unregisterGlobalObserver(observer);

            // Second query: observer is no longer active.
            var citiesAgain = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
            assertEquals(6, citiesAgain.size());
            assertEquals(countAfterFirst, observedSql.size(),
                    "Global observer should NOT have captured SQL after unregistration.");
        } finally {
            // Safety net: ensure the observer is removed even if the test fails.
            SqlInterceptor.unregisterGlobalObserver(observer);
        }
    }

    // -----------------------------------------------------------------------
    // 13. registerGlobalInterceptor / unregisterGlobalInterceptor
    // -----------------------------------------------------------------------

    @Test
    public void testGlobalInterceptorLifecycle() {
        List<String> interceptedSql = new ArrayList<>();
        UnaryOperator<Sql> interceptor = sql -> {
            interceptedSql.add(sql.statement());
            return sql;  // pass through unchanged
        };

        SqlInterceptor.registerGlobalInterceptor(interceptor);
        try {
            // First query: interceptor is active.
            var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
            assertEquals(6, cities.size());
            int countAfterFirst = interceptedSql.size();
            assertTrue(countAfterFirst > 0, "Global interceptor should have captured SQL during first query.");

            // Unregister the interceptor.
            SqlInterceptor.unregisterGlobalInterceptor(interceptor);

            // Second query: interceptor is no longer active.
            var citiesAgain = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
            assertEquals(6, citiesAgain.size());
            assertEquals(countAfterFirst, interceptedSql.size(),
                    "Global interceptor should NOT have captured SQL after unregistration.");
        } finally {
            // Safety net: ensure the interceptor is removed even if the test fails.
            SqlInterceptor.unregisterGlobalInterceptor(interceptor);
        }
    }

    // -----------------------------------------------------------------------
    // Additional: verify intercept can modify SQL statements (passthrough)
    // -----------------------------------------------------------------------

    @Test
    public void testInterceptModifiesSqlPassthrough() {
        AtomicReference<String> capturedStatement = new AtomicReference<>();
        SqlInterceptor.intercept(
                sql -> {
                    capturedStatement.set(sql.statement());
                    return sql;  // return the same SQL, no actual modification
                },
                () -> {
                    var count = ORMTemplate.of(dataSource).entity(City.class).select().getResultCount();
                    assertEquals(6, count);
                }
        );
        assertNotNull(capturedStatement.get());
        assertTrue(capturedStatement.get().toUpperCase().contains("SELECT"));
    }

    // -----------------------------------------------------------------------
    // Additional: nested observe scopes
    // -----------------------------------------------------------------------

    @Test
    public void testNestedObserveScopes() {
        List<String> outerSql = new ArrayList<>();
        List<String> innerSql = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> outerSql.add(sql.statement()),
                () -> {
                    // Outer query.
                    var cities = ORMTemplate.of(dataSource).entity(City.class).select().getResultList();
                    assertEquals(6, cities.size());

                    // Nested scope with separate observer.
                    long count = SqlInterceptor.observe(
                            sql -> innerSql.add(sql.statement()),
                            () -> ORMTemplate.of(dataSource).entity(City.class).select().getResultCount()
                    );
                    assertEquals(6, count);
                }
        );
        // Both outer and inner should have captured SQL.
        assertFalse(outerSql.isEmpty());
        assertFalse(innerSql.isEmpty());
        // The outer observer should also see the inner query's SQL (because it wraps the inner scope).
        assertTrue(outerSql.size() >= innerSql.size());
    }
}
