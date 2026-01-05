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
package st.orm.mapping;

import jakarta.annotation.Nonnull;

/**
 * Resolves the column name for a record component.
 */
@FunctionalInterface
public interface ColumnNameResolver {

    /**
     * The default column name resolver used by the ORM template.
     */
    ColumnNameResolver DEFAULT = camelCaseToSnakeCase();

    /**
     * Resolves the column name for a record component using camel case to snake case conversion.
     *
     * @return the column name resolver.
     */
    static ColumnNameResolver camelCaseToSnakeCase() {
        return field -> NameResolver.camelCaseToSnakeCase(field.name());
    }

    /**
     * Resolves the column name for a record component by converting the column name to upper case.
     *
     * @param resolver the column name resolver to wrap.
     * @return the column name resolver.
     */
    static ColumnNameResolver toUpperCase(@Nonnull ColumnNameResolver resolver) {
        return component -> resolver.resolveColumnName(component).toUpperCase();
    }

    /**
     * Resolves the column name for a record component.
     *
     * @param field the record field
     * @return the column name.
     */
    String resolveColumnName(@Nonnull RecordField field);
}
