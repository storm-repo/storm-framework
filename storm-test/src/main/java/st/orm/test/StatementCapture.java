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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlInterceptor;
import st.orm.core.template.SqlTemplate.Parameter;
import st.orm.test.CapturedStatement.Operation;

/**
 * Captures SQL statements generated during the execution of an action.
 *
 * <p>Uses a scoped SQL observer (thread-local) to record statements without interfering with the actual database
 * operations. Statements accumulate across multiple {@link #run}, {@link #execute}, or {@link #executeThrowing} calls;
 * use {@link #clear()} to reset between captures.</p>
 *
 * <p>This class is not thread-safe by design: the scoped observer is bound to the calling thread.</p>
 *
 * @since 1.9
 */
public final class StatementCapture {

    private final List<CapturedStatement> statements = new ArrayList<>();

    private Consumer<Sql> createObserver() {
        return sql -> {
            Operation op = switch (sql.operation()) {
                case SELECT -> Operation.SELECT;
                case INSERT -> Operation.INSERT;
                case UPDATE -> Operation.UPDATE;
                case DELETE -> Operation.DELETE;
                case UNDEFINED -> Operation.UNDEFINED;
            };
            List<Object> params = sql.parameters().stream()
                    .map(Parameter::dbValue)
                    .toList();
            statements.add(new CapturedStatement(op, sql.statement(), params));
        };
    }

    /**
     * Executes the given action while capturing all SQL statements it generates.
     *
     * @param action the action to execute.
     */
    public void run(Runnable action) {
        SqlInterceptor.observe(createObserver(), action);
    }

    /**
     * Executes the given action while capturing all SQL statements it generates, returning its result.
     *
     * @param action the action to execute.
     * @param <T> the result type.
     * @return the result of the action.
     */
    public <T> T execute(Supplier<T> action) {
        return SqlInterceptor.observe(createObserver(), action);
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
        return SqlInterceptor.observeThrowing(createObserver(), action);
    }

    /**
     * Returns all captured statements.
     *
     * @return an unmodifiable copy of the captured statements.
     */
    public List<CapturedStatement> statements() {
        return List.copyOf(statements);
    }

    /**
     * Returns captured statements filtered by operation type.
     *
     * @param operation the operation type to filter by.
     * @return the matching statements.
     */
    public List<CapturedStatement> statements(Operation operation) {
        return statements.stream()
                .filter(s -> s.operation() == operation)
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
     * Clears all captured statements.
     */
    public void clear() {
        statements.clear();
    }
}
