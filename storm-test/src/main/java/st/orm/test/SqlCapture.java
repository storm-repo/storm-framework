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
package st.orm.test;

import jakarta.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import st.orm.core.spi.QueryContext;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.core.template.impl.SqlInterceptorManager;
import st.orm.core.template.impl.StatementListener;
import st.orm.test.CapturedSql.Operation;
import st.orm.test.CapturedSql.Origin;

/**
 * Captures the SQL statements an action executes.
 *
 * <p>Statements are recorded around execution, so each carries what caused it, its bound parameter values, and how
 * long it took, and a statement that is built but never run is not captured. Statements accumulate across multiple
 * {@link #run}, {@link #execute}, or {@link #executeThrowing} calls; use {@link #clear()} to reset between
 * captures.</p>
 *
 * <p>The capture is bound to the calling thread and to the contexts Storm carries it into, such as a
 * {@code transaction} block; recording is safe from any thread the work reaches.</p>
 *
 * <p><strong>Testing only.</strong> Captured statements retain their bound parameter values, which may be
 * sensitive (credentials, personal data). This class lives in the test-scoped {@code storm-test} module; do
 * not route captured statements to logs or external systems from production code.</p>
 *
 * @since 1.9
 */
public final class SqlCapture {

    private final Queue<CapturedSql> statements = new ConcurrentLinkedQueue<>();

    /** Records each execution as it completes, so the capture carries the execution's duration. */
    private final StatementListener listener = new StatementListener() {
        @Override
        public Handle onExecute(@Nonnull QueryContext context, @Nonnull List<Parameter> parameters) {
            Operation op = switch (context.operation()) {
                case SELECT -> Operation.SELECT;
                case INSERT -> Operation.INSERT;
                case UPDATE -> Operation.UPDATE;
                case DELETE -> Operation.DELETE;
                case UNDEFINED -> Operation.UNDEFINED;
            };
            Origin origin = switch (context.origin()) {
                case DIRECT -> Origin.DIRECT;
                case FETCH -> Origin.FETCH;
            };
            String statement = context.statement().orElse("");
            List<Object> values = parameters.stream()
                    .map(Parameter::dbValue)
                    .toList();
            long start = System.nanoTime();
            return (rows, exact) -> statements.add(new CapturedSql(op, statement, values, origin,
                    Duration.ofNanos(System.nanoTime() - start), rows, exact));
        }
    };

    /**
     * Executes the given action while capturing all SQL statements it generates.
     *
     * @param action the action to execute.
     */
    public void run(Runnable action) {
        SqlInterceptorManager.listen(listener).run(action);
    }

    /**
     * Executes the given action while capturing all SQL statements it generates, returning its result.
     *
     * @param action the action to execute.
     * @param <T> the result type.
     * @return the result of the action.
     */
    public <T> T execute(Supplier<T> action) {
        return SqlInterceptorManager.listen(listener).get(action);
    }

    /**
     * Executes the given action while capturing all SQL statements it generates, returning its result and allowing
     * checked exceptions.
     *
     * @param action the action to execute.
     * @param <T> the result type.
     * @return the result of the action.
     * @throws Exception if the action throws an exception.
     */
    public <T> T executeThrowing(Callable<T> action) throws Exception {
        return SqlInterceptorManager.listen(listener).call(action);
    }

    /**
     * Returns all captured statements.
     *
     * @return an unmodifiable copy of the captured statements.
     */
    public List<CapturedSql> statements() {
        return List.copyOf(statements);
    }

    /**
     * Returns captured statements filtered by operation type.
     *
     * @param operation the operation type to filter by.
     * @return the matching statements.
     */
    public List<CapturedSql> statements(Operation operation) {
        return statements.stream()
                .filter(s -> s.operation() == operation)
                .toList();
    }

    /**
     * Returns captured statements filtered by what caused them to execute.
     *
     * @param origin the origin to filter by.
     * @return the matching statements.
     * @since 1.13
     */
    public List<CapturedSql> statements(Origin origin) {
        return statements.stream()
                .filter(s -> s.origin() == origin)
                .toList();
    }

    /**
     * Returns the total number of captured statements.
     *
     * @return the statement count.
     */
    public int count() {
        return statements.size();
    }

    /**
     * Returns the number of captured statements matching the given operation type.
     *
     * @param operation the operation type to count.
     * @return the matching statement count.
     */
    public int count(Operation operation) {
        return (int) statements.stream()
                .filter(s -> s.operation() == operation)
                .count();
    }

    /**
     * Returns the number of captured statements matching the given origin.
     *
     * <p>Asserting that {@link Origin#FETCH} stays at zero, or below a bound, holds a query to the shape its
     * fetch plan produces: the statements it counts are the ones a plan brings back in the same statement.</p>
     *
     * @param origin the origin to count.
     * @return the matching statement count.
     * @since 1.13
     */
    public int count(Origin origin) {
        return (int) statements.stream()
                .filter(s -> s.origin() == origin)
                .count();
    }

    /**
     * Clears all captured statements.
     */
    public void clear() {
        statements.clear();
    }
}
