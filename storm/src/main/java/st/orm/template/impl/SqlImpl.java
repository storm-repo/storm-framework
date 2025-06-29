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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.template.Sql;
import st.orm.template.SqlOperation;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.Parameter;

import java.util.List;
import java.util.Optional;

import static java.util.List.copyOf;
import static java.util.Optional.ofNullable;

/**
 * A result record that contains the generated SQL and the parameters that were used to generate it.
 *
 * @param operation     classifies the kind of SQL statement.
 * @param statement     the generated SQL with all parameters replaced by '?' or named ':name' placeholders.
 * @param parameters    the parameters that were used to generate the SQL.
 * @param bindVariables a bind variables object that can be used to add bind variables to a batch.
 * @param generatedKeys the primary key that have been auto generated as part of in insert statement.
 * @param versionAware  true if the statement is version aware, false otherwise.
 * @param unsafeWarning a warning message if the statement is deemed potentially unsafe, an empty optional otherwise.
 */
record SqlImpl(
        @Nonnull SqlOperation operation,
        @Nonnull String statement,
        @Nonnull List<Parameter> parameters,
        @Nonnull Optional<BindVariables> bindVariables,
        @Nonnull List<String> generatedKeys,
        boolean versionAware,
        @Nonnull Optional<String> unsafeWarning
) implements Sql {
    public SqlImpl {
        parameters = copyOf(parameters);
        generatedKeys = copyOf(generatedKeys);
    }

    /**
     * Returns a new instance of the SQL statement with the given operation.
     *
     * @param operation the SQL operation that classifies the kind of SQL statement.
     * @return a new instance of the SQL statement with the given operation.
     */
    @Override
    public Sql operation(@Nonnull SqlOperation operation) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the given statement.
     *
     * @param statement the new SQL statement.
     * @return a new instance of the SQL statement with the given statement.
     */
    @Override
    public Sql statement(@Nonnull String statement) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the given parameters.
     *
     * @param parameters the new parameters.
     * @return a new instance of the SQL statement with the given parameters.
     */
    @Override
    public Sql parameters(@Nonnull List<Parameter> parameters) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the given bind variables.
     *
     * @param bindVariables the new bind variables
     * @return a new instance of the SQL statement with the given bind variables
     */
    @Override
    public Sql bindVariables(@Nullable SqlTemplate.BindVariables bindVariables) {
        return new SqlImpl(operation, statement, parameters, ofNullable(bindVariables), generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the given generated keys.
     *
     * @param generatedKeys the new generated keys
     * @return a new instance of the SQL statement with the given generated keys
     * @since 1.2
     */
    @Override
    public Sql generatedKeys(@Nonnull List<String> generatedKeys) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the version aware flag set to the given value.
     *
     * @param versionAware the new value of the version aware flag.
     * @return a new instance of the SQL statement with the version aware flag set to the given value
     * @since 1.2
     */
    @Override
    public Sql versionAware(boolean versionAware) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, unsafeWarning);
    }

    /**
     * Returns a new instance of the SQL statement with the given unsafe warning message.
     *
     * @param unsafeWarning the new unsafe warning message
     * @return a new instance of the SQL statement with the given unsafe warning message
     * @since 1.2
     */
    @Override
    public Sql unsafeWarning(@Nullable String unsafeWarning) {
        return new SqlImpl(operation, statement, parameters, bindVariables, generatedKeys, versionAware, ofNullable(unsafeWarning));
    }
}
