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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Ref;

/**
 * Interface for creating ref instances for records.
 *
 * @since 1.3
 */
public interface RefFactory {

    /**
     * Creates a ref instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk);

    /**
     * Creates a ref instance for the specified {@code record} and {@code pk}. This method can be used to generate
     * ref instances for entities, projections and regular records. The object returned by this method already
     * contains the fetched record.
     *
     * @param pk primary key.
     * @return ref instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk);
}
