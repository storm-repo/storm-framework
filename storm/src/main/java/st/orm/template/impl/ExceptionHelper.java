/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.template.impl;

import jakarta.annotation.Nullable;
import st.orm.PersistenceException;
import st.orm.template.Sql;
import st.orm.template.SqlTemplateException;

import java.util.function.Function;

/**
 * Helper class for augmenting exceptions with SQL statements.
 *
 * @since 1.3
 */
class ExceptionHelper {

    private ExceptionHelper() {}

    static Function<Throwable, PersistenceException> getExceptionTransformer(@Nullable Sql sql) {
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
                    e.addSuppressed(new SqlTemplateException(String.format(
                            "SQL%n%s", sql.statement()
                    )));
                }
                throw ex;
            }
        };
    }
}
