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
package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.kotlin.template.KPredicateBuilder;
import st.orm.kotlin.template.impl.KQueryBuilderImpl.KPredicateBuilderImpl;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.PredicateBuilder;
import st.orm.template.impl.PredicateBuilderFactory;

/**
 * Factory for creating instances of {@link KPredicateBuilder}.
 *
 * This interface provides static methods to create predicate builders for Kotlin records, allowing for flexible query
 * construction.
 *
 * @since 1.3
 */
public interface KPredicateBuilderFactory {

    /**
     * Creates a new instance of {@link KPredicateBuilder} for the specified path, operator, and values.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <V> the type of the values
     * @return a new instance of {@link KPredicateBuilder}
     */
    static <T extends Record, R, V> KPredicateBuilder<T, R, ?> create(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<V> o) {
        return new KPredicateBuilderImpl<>(PredicateBuilderFactory.create(path, operator, o));
    }

    /**
     * Creates a new instance of {@link KPredicateBuilder} for the specified path, operator, and values.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <V> the type of the values
     * @return a new instance of {@link KPredicateBuilder}
     */
    static <T extends Record, R, V extends Record> KPredicateBuilder<T, R, ?> createRef(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<Ref<V>> o) {
        return new KPredicateBuilderImpl<>(PredicateBuilderFactory.createRef(path, operator, o));
    }

    /**
     * Creates a new instance of {@link KPredicateBuilder} for the specified path, operator, and values with an ID.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <ID> the type of the ID
     * @param <V> the type of the values
     * @return a new instance of {@link KPredicateBuilder}
     */
    static <T extends Record, R, ID, V> KPredicateBuilder<T, R, ID> createWithId(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<V> o) {
        return new KPredicateBuilderImpl<>(PredicateBuilderFactory.createWithId(path, operator, o));
    }
    /**
     * Creates a new instance of {@link KPredicateBuilder} for the specified path, operator, and values with an ID.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <ID> the type of the ID
     * @param <V> the type of the values
     * @return a new instance of {@link KPredicateBuilder}
     */
    static <T extends Record, R, ID, V extends Record> KPredicateBuilder<T, R, ID> createRefWithId(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<Ref<V>> o) {
        return new KPredicateBuilderImpl<>(PredicateBuilderFactory.createRefWithId(path, operator, o));
    }

    /**
     * Bridges a {@link KPredicateBuilder} to a {@link PredicateBuilder}.
     *
     * @param predicateBuilder the predicate builder to bridge.
     * @param <T> the type of the record.
     * @param <R> the type of the result.
     * @param <ID> the type of the ID.
     * @return a bridged instance of {@link PredicateBuilder}.
     */
    static <T extends Record, R, ID> PredicateBuilder<T, R, ID> bridge(@Nonnull KPredicateBuilder<T, R, ID> predicateBuilder) {
        return ((KPredicateBuilderImpl<T, R, ID>) predicateBuilder).predicateBuilder;
    }
}
