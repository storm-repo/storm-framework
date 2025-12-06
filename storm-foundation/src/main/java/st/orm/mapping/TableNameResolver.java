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
package st.orm.mapping;

import jakarta.annotation.Nonnull;

/**
 * Resolves the table name for a given record type.
 */
@FunctionalInterface
public interface TableNameResolver {

    /**
     * The default table name resolver.
     */
    TableNameResolver DEFAULT = camelCaseToSnakeCase();

    /**
     * Resolves the table name for a given record type using camel case to snake case conversion.
     *
     * @return the table name resolver.
     */
    static TableNameResolver camelCaseToSnakeCase() {
        return type -> NameResolver.camelCaseToSnakeCase(type.type().getSimpleName());
    }

    /**
     * Resolves the table name for a record by converting the table name to upper case.
     *
     * @param resolver the table name resolver to wrap.
     * @return the table name resolver.
     */
    static TableNameResolver toUpperCase(@Nonnull TableNameResolver resolver) {
        return type -> resolver.resolveTableName(type).toUpperCase();
    }

    /**
     * Resolves the table name for a given record type.
     *
     * @param type the record type.
     * @return the table name.
     */
    String resolveTableName(@Nonnull RecordType type);
}
