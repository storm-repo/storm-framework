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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import st.orm.PersistenceException;
import st.orm.SqlLog;
import st.orm.core.template.SqlInterceptor;

/**
 * Shared utility for processing {@link SqlLog} annotations in repository proxies.
 *
 * <p>This interceptor is used by all three {@code ORMTemplateImpl} classes (core, java21, kotlin)
 * to wrap method invocations with SQL logging when the {@link SqlLog} annotation is present.</p>
 *
 * @since 1.9
 */
public final class SqlLogInterceptor {

    private SqlLogInterceptor() {}

    /**
     * Resolves the effective {@link SqlLog} annotation for a method, considering both method-level and type-level
     * annotations. Method-level annotations take precedence over type-level annotations.
     *
     * @param repositoryType the repository interface type.
     * @param method the method being invoked.
     * @return the effective {@link SqlLog} annotation, or {@code null} if not present.
     */
    @Nullable
    public static SqlLog resolve(@Nonnull Class<?> repositoryType, @Nonnull Method method) {
        SqlLog methodLevel = method.getAnnotation(SqlLog.class);
        if (methodLevel != null) {
            return methodLevel;
        }
        return repositoryType.getAnnotation(SqlLog.class);
    }

    /**
     * Wraps a callable with SQL interception and logging if the given {@link SqlLog} annotation is active and the
     * logger is enabled for the specified level. If the annotation is {@code null} or the logger is not enabled,
     * the callable is executed directly.
     *
     * @param sqlLog the resolved {@link SqlLog} annotation, or {@code null}.
     * @param repositoryType the repository interface type (used for logger name and log prefix).
     * @param methodName the short method signature string (used in log output).
     * @param callable the action to execute.
     * @param <T> the return type.
     * @return the result of the callable.
     * @throws Throwable if the callable throws.
     */
    public static <T> T wrapIfNeeded(@Nullable SqlLog sqlLog,
                                     @Nonnull Class<?> repositoryType,
                                     @Nonnull String methodName,
                                     @Nonnull Callable<T> callable) throws Throwable {
        if (sqlLog == null) {
            return callable.call();
        }
        String loggerName = sqlLog.name().isEmpty() ? repositoryType.getName() : sqlLog.name();
        Logger logger = System.getLogger(loggerName);
        Level level = sqlLog.level();
        if (!logger.isLoggable(level)) {
            return callable.call();
        }
        String signature = "%s.%s".formatted(repositoryType.getSimpleName(), methodName);
        try {
            return SqlInterceptor.interceptThrowing(
                    template -> template.withInlineParameters(sqlLog.inlineParameters()),
                    sql -> {
                        String indentedSql = sql.statement()
                                .lines()
                                .map(line -> "\t" + line)
                                .collect(Collectors.joining("\n"));
                        logger.log(level, "[SQL] (%s)\n%s".formatted(signature, indentedSql));
                        return sql;
                    },
                    callable
            );
        } catch (Exception e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}
