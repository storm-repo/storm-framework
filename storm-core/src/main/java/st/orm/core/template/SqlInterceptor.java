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
package st.orm.core.template;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import st.orm.core.template.impl.SqlInterceptorManager;

/**
 * Interceptor for SQL statement generation events.
 *
 * <p>This interceptor is invoked during the generation of SQL statements by the {@link SqlTemplate}, and not during
 * their execution. It allows you to inspect SQL statements before they are sent to the database, operating completely
 * separate from the execution phase.</p>
 *
 * @since 1.1
 */
public interface SqlInterceptor {

    /**
     * Register a global observer that will be called for all SQL statements being generated.
     *
     * @param observer the observer to call for each SQL statement.
     */
    static void registerGlobalObserver(Consumer<Sql> observer) {
        SqlInterceptorManager.registerGlobalObserver(observer);
    }

    /**
     * Register a global interceptor that will be called for all SQL statements being generated.
     *
     * @param interceptor the interceptor to call for each SQL statement.
     */
    static void registerGlobalInterceptor(UnaryOperator<Sql> interceptor) {
        SqlInterceptorManager.registerGlobalInterceptor(interceptor);
    }

    /**
     * Unregister a global observer.
     *
     * @param observer the observer to unregister.
     */
    static void unregisterGlobalObserver(Consumer<Sql> observer) {
        SqlInterceptorManager.unregisterGlobalObserver(observer);
    }

    /**
     * Unregister a global interceptor.
     *
     * @param interceptor the interceptor to unregister.
     */
    static void unregisterGlobalInterceptor(UnaryOperator<Sql> interceptor) {
        SqlInterceptorManager.unregisterGlobalObserver(interceptor);
    }

    /**
     * Executes a {@code Runnable} action within the context of an SQL observer, which consumes SQL statements.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code runnable}.</p>
     *
     * @param observer the consumer invoked for each SQL statement.
     * @param runnable the action to execute.
     */
    static void observe(Consumer<Sql> observer, Runnable runnable) {
        SqlInterceptorManager.intercept(observer).run(runnable);
    }

    /**
     * Executes a {@code Supplier} within the context of an SQL observer, returning its result.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code supplier}.</p>
     *
     * @param observer the consumer invoked for each SQL statement.
     * @param supplier the action supplying the result.
     * @param <T> the type of the supplied result.
     * @return the result of the supplied action.
     */
    static <T> T observe(Consumer<Sql> observer, Supplier<T> supplier) {
        return SqlInterceptorManager.intercept(observer).get(supplier);
    }

    /**
     * Executes a {@code Callable} action within the context of an SQL observer, returning its result and potentially
     * throwing exceptions.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code callable}.</p>
     *
     * @param observer the consumer invoked for each SQL statement.
     * @param callable the action supplying the result and potentially throwing exceptions.
     * @param <T> the type of the result.
     * @return the result of the callable action.
     * @throws Exception if the callable action throws an exception.
     */
    static <T> T observeThrowing(Consumer<Sql> observer, Callable<T> callable) throws Exception {
        return SqlInterceptorManager.intercept(observer).call(callable);
    }

    /**
     * Executes a {@code Runnable} action within the context of an SQL observer, which consumes SQL statements.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code runnable}.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param observer the consumer invoked for each SQL statement.
     * @param runnable the action to execute.
     * @since 1.3
     */
    static void observe(UnaryOperator<SqlTemplate> customizer,
                        Consumer<Sql> observer,
                        Runnable runnable) {
        SqlInterceptorManager.intercept(customizer, observer).run(runnable);
    }

    /**
     * Executes a {@code Supplier} within the context of an SQL observer, returning its result.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code supplier}.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param observer the consumer invoked for each SQL statement.
     * @param supplier the action supplying the result.
     * @param <T> the type of the supplied result.
     * @return the result of the supplied action.
     * @since 1.3
     */
    static <T> T observe(UnaryOperator<SqlTemplate> customizer,
                         Consumer<Sql> observer,
                         Supplier<T> supplier) {
        return SqlInterceptorManager.intercept(customizer, observer).get(supplier);
    }

    /**
     * Executes a {@code Callable} action within the context of an SQL observer, returning its result and potentially
     * throwing exceptions.
     *
     * <p>This observer sees only SQL statements generated within the scope of this {@code callable}.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param observer the consumer invoked for each SQL statement.
     * @param callable the action supplying the result and potentially throwing exceptions.
     * @param <T> the type of the result.
     * @return the result of the callable action.
     * @throws Exception if the callable action throws an exception.
     * @since 1.3
     */
    static <T> T observeThrowing(UnaryOperator<SqlTemplate> customizer,
                                 Consumer<Sql> observer,
                                 Callable<T> callable) throws Exception {
        return SqlInterceptorManager.intercept(customizer, observer).call(callable);
    }

    /**
     * Executes a {@code Runnable} action within the context of an SQL interceptor, which modifies SQL statements.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code runnable}.</p>
     *
     * @param interceptor the operator applied to each SQL statement.
     * @param runnable the action to execute.
     */
    static void intercept(UnaryOperator<Sql> interceptor, Runnable runnable) {
        SqlInterceptorManager.intercept(interceptor).run(runnable);
    }

    /**
     * Executes a {@code Supplier} within the context of an SQL interceptor, returning its result.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code supplier}.</p>
     *
     * @param interceptor the operator applied to each SQL statement.
     * @param supplier the action supplying the result.
     * @param <T> the type of the supplied result.
     * @return the result of the supplied action.
     */
    static <T> T intercept(UnaryOperator<Sql> interceptor, Supplier<T> supplier) {
        return SqlInterceptorManager.intercept(interceptor).get(supplier);
    }

    /**
     * Executes a {@code Callable} action within the context of an SQL interceptor, returning its result and potentially
     * throwing exceptions.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code callable}.</p>
     *
     * @param interceptor the operator applied to each SQL statement.
     * @param callable the action supplying the result and potentially throwing exceptions.
     * @param <T> the type of the result.
     * @return the result of the callable action.
     * @throws Exception if the callable action throws an exception.
     */
    static <T> T interceptThrowing(UnaryOperator<Sql> interceptor, Callable<T> callable) throws Exception {
        return SqlInterceptorManager.intercept(interceptor).call(callable);
    }

    /**
     * Executes a {@code Runnable} action within the context of an SQL interceptor, which modifies SQL statements.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code runnable}.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param interceptor the operator applied to each SQL statement.
     * @param runnable the action to execute.
     * @since 1.3
     */
    static void intercept(UnaryOperator<SqlTemplate> customizer,
                          UnaryOperator<Sql> interceptor,
                          Runnable runnable) {
        SqlInterceptorManager.intercept(customizer, interceptor).run(runnable);
    }

    /**
     * Executes a {@code Supplier} within the context of an SQL interceptor, returning its result.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code supplier}.
     *
     * @param customizer a function to customize the SQL template before use.
     * @param interceptor the operator applied to each SQL statement.
     * @param supplier the action supplying the result.
     * @param <T> the type of the supplied result.
     * @return the result of the supplied action.
     * @since 1.3
     */
    static <T> T intercept(UnaryOperator<SqlTemplate> customizer,
                           UnaryOperator<Sql> interceptor,
                           Supplier<T> supplier) {
        return SqlInterceptorManager.intercept(customizer, interceptor).get(supplier);
    }

    /**
     * Executes a {@code Callable} action within the context of an SQL interceptor, returning its result and potentially
     * throwing exceptions.
     *
     * <p>This interceptor sees only SQL statements generated within the scope of this {@code callable}.</p>
     *
     * @param customizer a function to customize the SQL template before use.
     * @param interceptor the operator applied to each SQL statement.
     * @param callable the action supplying the result and potentially throwing exceptions.
     * @param <T> the type of the result.
     * @return the result of the callable action.
     * @throws Exception if the callable action throws an exception.
     * @since 1.3
     */
    static <T> T interceptThrowing(UnaryOperator<SqlTemplate> customizer,
                                   UnaryOperator<Sql> interceptor,
                                   Callable<T> callable) throws Exception {
        return SqlInterceptorManager.intercept(customizer, interceptor).call(callable);
    }
}
