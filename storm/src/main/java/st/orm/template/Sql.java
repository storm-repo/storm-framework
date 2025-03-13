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
package st.orm.template;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.Parameter;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.Table;

import java.util.List;
import java.util.Optional;

/**
 * Represents the generated SQL statement with parameters.
 */
public interface Sql {

    /**
     * The generated SQL with all parameters replaced by '?' or named ':name' placeholders.
     */
    String statement();

    /**
     * The parameters that were used to generate the SQL.
     */
    List<Parameter> parameters();

    /**
     * A bind variables object that can be used to add bind variables to a batch, if available.
     */
    Optional<BindVariables> bindVariables();

    /**
     * The primary key that have been auto generated as part of in insert statement.
     *
     * <p>Note that the generated keys are only set when the SQL is generated using an {@link Insert} element, either
     * directly or indirectly using {@link Table}.</p>
     */
    List<String> generatedKeys();

    /**
     * Returns a new instance of the SQL statement with the given generated keys.
     *
     * @param generatedKeys the new generated keys
     * @return a new instance of the SQL statement with the given generated keys
     * @since 1.2
     */
    Sql generatedKeys(@Nonnull List<String> generatedKeys);

    /**
     * Returns {@code true} if the statement is version aware, {@code false} otherwise.
     *
     * <p>Note that the version aware flag is only set when the SQL is generated using a {@link Set} element.</p>
     */
    boolean versionAware();

    /**
     * Returns a new instance of the SQL statement with the version aware flag set to the given value.
     *
     * @param versionAware the new value of the version aware flag.
     * @return a new instance of the SQL statement with the version aware flag set to the given value
     * @since 1.2
     */
    Sql versionAware(boolean versionAware);

    /**
     * Returns a warning message if the statement is deemed potentially unsafe, an empty optional otherwise.
     *
     * @since 1.2
     */
    Optional<String> unsafeWarning();

    /**
     * Returns a new instance of the SQL statement with the given unsafe warning message.
     *
     * @param unsafeWarning the new unsafe warning message
     * @return a new instance of the SQL statement with the given unsafe warning message
     * @since 1.2
     */
    Sql unsafeWarning(@Nullable String unsafeWarning);
}
