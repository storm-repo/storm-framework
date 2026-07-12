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
package st.orm.micrometer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.tracing.test.simple.SimpleTracer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;
import st.orm.core.template.ORMTemplate;

/**
 * Tests for the SQL commenter: comment content is appended to statements at execution time, hostile content
 * is rejected, and {@link TraceContextSqlCommenter} renders the current trace context in W3C traceparent
 * format.
 */
public class SqlCommenterTest {

    /** Wraps a DataSource, recording the SQL of every prepared statement. */
    private static DataSource capturing(DataSource delegate, List<String> statements) {
        return (DataSource) Proxy.newProxyInstance(SqlCommenterTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    Object result = method.invoke(delegate, args);
                    if (result instanceof Connection connection) {
                        return Proxy.newProxyInstance(SqlCommenterTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, connectionHandler(connection, statements));
                    }
                    return result;
                });
    }

    private static InvocationHandler connectionHandler(Connection connection, List<String> statements) {
        return (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql) {
                statements.add(sql);
            }
            return method.invoke(connection, args);
        };
    }

    @Test
    public void commentIsAppendedAtExecutionTime() {
        List<String> statements = new ArrayList<>();
        var orm = ORMTemplate.builder(capturing(
                        new SimpleDataSource("jdbc:h2:mem:commenter;DB_CLOSE_DELAY=-1"), statements))
                .sqlCommenter(() -> Optional.of("traceparent='00-abc-def-01'"))
                .build();
        orm.query("SELECT 1").getSingleResult(Integer.class);
        assertTrue(statements.stream().anyMatch(sql -> sql.endsWith("/* traceparent='00-abc-def-01' */")),
                statements.toString());
    }

    @Test
    public void emptyCommentLeavesTheStatementUntouched() {
        List<String> statements = new ArrayList<>();
        var orm = ORMTemplate.builder(capturing(
                        new SimpleDataSource("jdbc:h2:mem:commenterempty;DB_CLOSE_DELAY=-1"), statements))
                .sqlCommenter(Optional::empty)
                .build();
        orm.query("SELECT 1").getSingleResult(Integer.class);
        assertFalse(statements.stream().anyMatch(sql -> sql.contains("/*")), statements.toString());
    }

    @Test
    public void hostileCommentContentIsRejected() {
        var orm = ORMTemplate.builder(new SimpleDataSource("jdbc:h2:mem:commenterhostile;DB_CLOSE_DELAY=-1"))
                .sqlCommenter(() -> Optional.of("x'*/ DROP TABLE pets; --"))
                .build();
        assertThrows(PersistenceException.class, () -> orm.query("SELECT 1").getSingleResult(Integer.class));
    }

    @Test
    public void semicolonsInCommentContentAreRejected() {
        // Inert to the SQL parser inside a comment, but naive statement splitters in drivers and proxies
        // split on semicolons; sqlcommenter values URL-encode them instead.
        var orm = ORMTemplate.builder(new SimpleDataSource("jdbc:h2:mem:commentersemi;DB_CLOSE_DELAY=-1"))
                .sqlCommenter(() -> Optional.of("route='a;b'"))
                .build();
        assertThrows(PersistenceException.class, () -> orm.query("SELECT 1").getSingleResult(Integer.class));
    }

    @Test
    public void executableCommentMarkersAreNeutralizedByPadding() {
        // MySQL and MariaDB interpret /*! ... */ as an executable comment and /*+ ... */ as an optimizer
        // hint; the padding space after the comment opener keeps such content a plain comment.
        List<String> statements = new ArrayList<>();
        var orm = ORMTemplate.builder(capturing(
                        new SimpleDataSource("jdbc:h2:mem:commenterexec;DB_CLOSE_DELAY=-1"), statements))
                .sqlCommenter(() -> Optional.of("!40101 SELECT 1"))
                .build();
        orm.query("SELECT 1").getSingleResult(Integer.class);
        assertTrue(statements.stream().anyMatch(sql -> sql.endsWith("/* !40101 SELECT 1 */")),
                statements.toString());
    }

    @Test
    public void sampledOnlyModeSkipsUnsampledSpans() {
        var tracer = new SimpleTracer();
        var sampledOnly = new TraceContextSqlCommenter(tracer, true);
        var always = new TraceContextSqlCommenter(tracer);
        var span = tracer.nextSpan().start();
        try (var ignored = tracer.withSpan(span)) {
            // SimpleTracer spans are not sampled: the sampled-only commenter stays silent while the
            // default commenter still renders the trace identity.
            assertEquals(Optional.empty(), sampledOnly.comment());
            assertTrue(always.comment().isPresent());
        } finally {
            span.end();
        }
    }

    @Test
    public void traceContextCommenterRendersTraceparent() {
        var tracer = new SimpleTracer();
        var commenter = new TraceContextSqlCommenter(tracer);
        assertEquals(Optional.empty(), commenter.comment());
        var span = tracer.nextSpan().start();
        try (var ignored = tracer.withSpan(span)) {
            var comment = commenter.comment().orElseThrow();
            assertTrue(comment.matches("traceparent='00-[0-9a-f]+-[0-9a-f]+-0[01]'"), comment);
        } finally {
            span.end();
        }
        assertEquals(Optional.empty(), commenter.comment());
    }
}
