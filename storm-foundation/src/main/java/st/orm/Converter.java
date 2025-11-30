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
package st.orm;

import jakarta.annotation.Nullable;

/**
 * Converts between an entity value type and a database column type.
 *
 * <p>A converter maps a component value of type {@code E} to a JDBC-compatible value of type {@code D}, and back again.
 * Converters are used when a record component is not directly supported by the SQL driver or when a custom mapping is
 * desired.</p>
 *
 * <p>Conversion is applied in one of the following ways:</p>
 *
 * <ul>
 *     <li><strong>Explicit conversion</strong>
 *         A converter can be selected directly through {@link st.orm.Convert @Convert}.</li>
 *     <li><strong>Auto-apply</strong>
 *         A converter can opt in by overriding {@link #autoApply()} and returning {@code true}. Auto-apply converters
 *         are used only when no explicit converter is present.</li>
 *     <li><strong>No conversion</strong>
 *         If a component has neither an explicit converter nor an applicable auto-apply converter, Storm uses the
 *         built-in mapping.</li>
 * </ul>
 *
 * <p>Implementations must provide a public no-argument constructor so Storm can instantiate them during classpath
 * scanning.</p>
 *
 * @param <D> the database-visible type (JDBC-compatible).
 * @param <E> the entity value type.
 * @since 1.7
 */
public interface Converter<D, E> {

    /**
     * Indicates whether this converter should be picked up automatically when a component type matches and no explicit
     * converter is set.
     *
     * <p>The default is {@code false}. Override and return {@code true} to make the converter eligible for auto-apply
     * resolution.</p>
     */
    default boolean autoApply() {
        return false;
    }

    /**
     * Converts an entity value to a database column value.
     *
     * @param value the entity value, possibly {@code null}.
     * @return the database-compatible value, possibly {@code null}.
     */
    D toDatabase(@Nullable E value);

    /**
     * Converts a database column value to an entity value.
     *
     * @param dbValue the column value read from the database, possibly {@code null}.
     * @return the converted entity value, possibly {@code null}.
     */
    E fromDatabase(@Nullable D dbValue);
}