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
package st.orm.config;

import jakarta.annotation.Nonnull;

import java.lang.reflect.RecordComponent;

/**
 * Resolves the column name for a foreign key component.
 */
@FunctionalInterface
public interface ForeignKeyResolver {

    /**
     * The default foreign key resolver used by the ORM template.
     */
    ForeignKeyResolver DEFAULT = camelCaseToSnakeCase();

    /**
     * Resolves the column name for a record component using camel case to snake case conversion.
     *
     * @return the column name resolver.
     */
    static ForeignKeyResolver camelCaseToSnakeCase() {
        return (component, ignore) -> NameResolver.camelCaseToSnakeCase(component.getName()) + "_id";
    }

    /**
     * Resolves the column name for a record component by converting the column name to upper case.
     *
     * @param resolver the column name resolver to wrap.
     * @return the column name resolver.
     */
    static ForeignKeyResolver toUpperCase(@Nonnull ForeignKeyResolver resolver) {
        return (component, type) -> resolver.resolveColumnName(component, type).toUpperCase();
    }

    /**
     * Resolves the column name for a foreign key record type.
     *
     * @param component the record component
     * @param type the record type to resolve the column name for.
     * @return the column name.
     */
    String resolveColumnName(@Nonnull RecordComponent component,
                             @Nonnull Class<? extends Record> type);
}
