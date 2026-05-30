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
import java.util.function.Function;
import st.orm.PersistenceException;
import st.orm.core.spi.Providers;
import st.orm.core.spi.TransactionContext;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplateException;

/**
 * Helper class for augmenting exceptions with SQL statements.
 *
 * @since 1.3
 */
public final class ExceptionHelper {

    private ExceptionHelper() {}

    public static Function<Throwable, PersistenceException> getExceptionTransformer(@Nullable Sql sql) {
        return e -> {
            try {
                try {
                    throw e;
                } catch (PersistenceException ex) {
                    throw ex;
                } catch (Throwable t) {
                    throw new PersistenceException(t);
                }
            } catch (PersistenceException ex) {
                if (sql != null) {
                    e.addSuppressed(new SqlTemplateException(buildSqlDetail(sql)));
                }
                throw ex;
            }
        };
    }

    private static String buildSqlDetail(@Nonnull Sql sql) {
        String detail = String.format("SQL:%n%s", sql.statement());
        String transaction = currentTransactionDescription();
        if (transaction != null) {
            detail = detail + String.format("%nTransaction: %s", transaction);
        }
        return detail;
    }

    /**
     * Returns a description of the current transaction's characteristics (such as isolation level and timeout), or
     * {@code null} when no transaction is active or the description cannot be determined.
     */
    private static @Nullable String currentTransactionDescription() {
        try {
            return Providers.getTransactionTemplate().currentContext()
                    .flatMap(TransactionContext::describe)
                    .orElse(null);
        } catch (Throwable ignore) {
            // Never let diagnostic enrichment mask the original exception.
            return null;
        }
    }
}
