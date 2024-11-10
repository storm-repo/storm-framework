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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Lazy;

/**
 * Interface for creating lazy instances for records.
 */
public interface LazyFactory {

    /**
     * Creates a lazy instance for the specified record {@code type} and {@code pk}. This method can be used to generate
     * lazy instances for entities, projections and regular records.
     *
     * @param type record type.
     * @param pk primary key.
     * @return lazy instance.
     * @param <T> record type.
     * @param <ID> primary key type.
     */
    <T extends Record, ID> Lazy<T, ID> create(@Nonnull Class<T> type, @Nullable ID pk);
}
