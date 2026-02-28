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

/**
 * Determines whether a Java type is compatible with a SQL column type.
 *
 * <p>Implementations define the mapping between Java types and JDBC SQL types
 * (as defined in {@link java.sql.Types}).</p>
 *
 * @since 1.9
 */
public interface TypeCompatibility {

    /**
     * Describes the compatibility between a Java type and a SQL column type.
     */
    enum Compatibility {
        /** Types are fully compatible. */
        COMPATIBLE,
        /** Types are numerically convertible but with potential precision or range differences. */
        NARROWING,
        /** Types are not compatible. */
        INCOMPATIBLE
    }

    /**
     * Checks the compatibility between the given Java type and the specified SQL column type.
     *
     * @param javaType    the Java type of the entity field (after converter application).
     * @param sqlType     the SQL type code from {@link java.sql.Types}.
     * @param sqlTypeName the database-specific type name (e.g., "uuid", "jsonb").
     * @return the compatibility result.
     */
    Compatibility check(@Nonnull Class<?> javaType, int sqlType, @Nonnull String sqlTypeName);

    /**
     * Returns the default type compatibility implementation.
     *
     * @return the default type compatibility.
     */
    static TypeCompatibility defaultCompatibility() {
        return DefaultTypeCompatibility.INSTANCE;
    }
}
