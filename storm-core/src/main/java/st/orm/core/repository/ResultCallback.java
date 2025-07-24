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
package st.orm.core.repository;

import jakarta.annotation.Nonnull;

import java.util.stream.Stream;

/**
 * Result callback interface.
 *
 * @param <T> input stream.
 * @param <R> result of the processing.
 */
@FunctionalInterface
public interface ResultCallback<T, R> {

    /**
     * Process the given stream.
     *
     * @param stream stream to process.
     * @return the result of the processing.
     */
    R process(@Nonnull Stream<T> stream);
}
