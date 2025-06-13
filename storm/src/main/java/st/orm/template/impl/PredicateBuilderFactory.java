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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Ref;
import st.orm.template.Metamodel;
import st.orm.template.Operator;
import st.orm.template.QueryBuilder.PredicateBuilder;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.QueryBuilderImpl.PredicateBuilderImpl;

import static java.lang.StringTemplate.RAW;

/**
 * Factory for creating instances of {@link PredicateBuilder}.
 *
 * This interface provides static methods to create predicate builders for records, allowing for flexible query
 * construction.
 *
 * @since 1.3
 */
public interface PredicateBuilderFactory {

    /**
     * Creates a new instance of {@link PredicateBuilder} for the specified path, operator, and values.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <V> the type of the values
     * @return a new instance of {@link PredicateBuilder}
     */
    static <T extends Record, R, V> PredicateBuilder<T, R, ?> create(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<V> o) {
        return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, o)}");
    }

    /**
     * Creates a new instance of {@link PredicateBuilder} for the specified path, operator, and values.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <V> the type of the values
     * @return a new instance of {@link PredicateBuilder}
     */
    static <T extends Record, R, V extends Record> PredicateBuilder<T, R, ?> createRef(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<Ref<V>> o) {
        return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, o)}");
    }

    /**
     * Creates a new instance of {@link PredicateBuilder} for the specified path, operator, and values with an ID.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <ID> the type of the ID
     * @param <V> the type of the values
     * @return a new instance of {@link PredicateBuilder}
     */
    static <T extends Record, R, ID, V> PredicateBuilder<T, R, ID> createWithId(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<V> o) {
        return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, o)}");
    }
    /**
     * Creates a new instance of {@link PredicateBuilder} for the specified path, operator, and values with an ID.
     *
     * @param path the metamodel path representing the field to be queried
     * @param operator the operator to be used in the predicate
     * @param o the values to be used in the predicate
     * @param <T> the type of the record
     * @param <R> the type of the result
     * @param <ID> the type of the ID
     * @param <V> the type of the values
     * @return a new instance of {@link PredicateBuilder}
     */
    static <T extends Record, R, ID, V extends Record> PredicateBuilder<T, R, ID> createRefWithId(
            @Nonnull Metamodel<?, V> path,
            @Nonnull Operator operator,
            @Nonnull Iterable<Ref<V>> o) {
        return new PredicateBuilderImpl<>(RAW."\{new ObjectExpression(path, operator, o)}");
    }
}
