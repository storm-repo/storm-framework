/*
 * Copyright 2024 the original author or authors.
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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.template.SqlTemplateException;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a specific SQL dialect with methods to determine feature support and handle identifier escaping.
 *
 * @since 1.1
 */
public interface SqlDialect {

    /**
     * Indicates whether the SQL dialect supports delete aliases.
     *
     * <p>Delete aliases allow delete statements to use table aliases in joins,  making it easier to filter rows based
     * on related data.</p>
     *
     * @return {@code true} if delete aliases are supported, {@code false} otherwise.
     */
    boolean supportsDeleteAlias();

    /**
     * Indicates whether the SQL dialect supports multi-value tuples in the IN clause.
     *
     * @return {@code true} if multi-value tuples are supported, {@code false} otherwise.
     * @since 1.2
     */
    boolean supportsMultiValueTuples();

    /**
     * Escapes the given database identifier (e.g., table or column name) according to this SQL dialect.
     *
     * @param name the identifier to escape (must not be {@code null})
     * @return the escaped identifier
     */
    String escape(@Nonnull String name);

    /**
     * Returns a string for the given column name.
     *
     * @param values the (multi) values to use in the IN clause.
     * @param parameterConsumer the consumer for the parameters.
     * @return the string that represents the multi value IN clause.
     * @throws SqlTemplateException if the values are incompatible.
     * @since 1.2
     */
    String multiValueIn(@Nonnull List<Map<String, Object>> values,
                        @Nonnull Consumer<Object> parameterConsumer) throws SqlTemplateException;

    /**
     * Returns a string template for the given limit.
     *
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit.
     * @since 1.2
     */
    String limit(int limit);

    /**
     * Returns a string template for the given limit and offset.
     *
     * @param offset the offset.
     * @param limit the maximum number of records to return.
     * @return a string template for the given limit and offset.
     * @since 1.2
     */
    String limit(int limit, int offset);
}