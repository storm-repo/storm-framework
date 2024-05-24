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
package st.orm.spi;

import jakarta.annotation.Nonnull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.stream.Stream;

/**
 * Interface for classes that can be ordered.
 */
public interface Orderable<T extends Orderable<T>> {

    /**
     * Ordering constraint that indicates that the current class should come before any other class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface BeforeAny {
    }

    /**
     * Ordering constraint that indicates that the current class should come before the specified classes.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Before {

        /**
         * The orderable classes that the current class should come before.
         *
         * @return orderable classes that the current class should come before.
         */
        Class<?>[] value();
    }

    /**
     * Ordering constraint that indicates that the current class should come after the specified classes.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface After {

        /**
         * The orderable classes that the current class should come after.
         *
         * @return orderable classes that the current class should come after.
         */
        Class<?>[] value();
    }

    /**
     * Ordering constraint that indicates that the current class should come after any other class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface AfterAny {
    }

    /**
     * Sorts the provided {@code Orderable} instances based on the ordering constraints defined by the orderable
     * constraints.
     *
     * @param <T> the type parameter for the {@code Orderable} instances.
     * @param orderables the orderable instances to sort.
     * @return the sorted orderable instances.
     */
    static <T extends Orderable<?>> List<T> sort(@Nonnull List<T> orderables) {
        return OrderableHelper.sort(orderables);
    }

    /**
     * Sorts the provided {@code Orderable} instances based on the ordering constraints defined by the orderable
     * constraints.
     *
     * @param <T> the type parameter for the {@code Orderable} instances.
     * @param orderables the orderable instances to sort.
     * @param cache use caching for improved performance.
     * @return the sorted orderable instances.
     */
    static <T extends Orderable<?>> List<T> sort(@Nonnull List<T> orderables, boolean cache) {
        return OrderableHelper.sort(orderables, cache);
    }

    /**
     * Sorts the provided {@code Orderable} instances based on the ordering constraints defined by the orderable
     * constraints.
     *
     * @param <T> the type parameter for the {@code Orderable} instances.
     * @param orderableStream the orderable instances to sort.
     * @return the sorted orderable instances.
     */
    static <T extends Orderable<?>> Stream<T> sort(@Nonnull Stream<T> orderableStream) {
        return OrderableHelper.sort(orderableStream);
    }

    /**
     * Sorts the provided {@code Orderable} instances based on the ordering constraints defined by the orderable
     * constraints.
     *
     * @param <T> the type parameter for the {@code Orderable} instances.
     * @param cache use caching for improved performance.
     * @param orderableStream the orderable instances to sort.
     * @return the sorted orderable instances.
     */
    static <T extends Orderable<?>> Stream<T> sort(@Nonnull Stream<T> orderableStream, boolean cache) {
        return OrderableHelper.sort(orderableStream, cache);
    }
}
