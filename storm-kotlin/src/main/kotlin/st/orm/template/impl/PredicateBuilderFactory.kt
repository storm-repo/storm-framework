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
package st.orm.template.impl

import st.orm.Data
import st.orm.Metamodel
import st.orm.Operator
import st.orm.Ref
import st.orm.core.template.impl.PredicateBuilderFactory
import st.orm.template.PredicateBuilder
import st.orm.template.impl.QueryBuilderImpl.PredicateBuilderImpl

/**
 * Creates a new instance of [PredicateBuilder] for the specified path, operator, and values.
 *
 * @param path the metamodel path representing the field to be queried
 * @param operator the operator to be used in the predicate
 * @param o the values to be used in the predicate
 * @param <T> the type of the record
 * @param <R> the type of the result
 * @param <V> the type of the values
 * @return a new instance of [PredicateBuilder]
*/
fun <T : Data, R, V> create(
    path: Metamodel<*, V>,
    operator: Operator,
    o: Iterable<V>
): PredicateBuilder<T, R, *> {
    return PredicateBuilderImpl(PredicateBuilderFactory.create(path, operator, o))
}

/**
 * Creates a new instance of [PredicateBuilder] for the specified path, operator, and values.
 *
 * @param path the metamodel path representing the field to be queried
 * @param operator the operator to be used in the predicate
 * @param o the values to be used in the predicate
 * @param <T> the type of the record
 * @param <R> the type of the result
 * @param <V> the type of the values
 * @return a new instance of [PredicateBuilder]
 */
fun <T : Data, R, V : Data> createRef(
    path: Metamodel<*, V>,
    operator: Operator,
    o: Iterable<Ref<V>>
): PredicateBuilder<T, R, *> {
    return PredicateBuilderImpl(PredicateBuilderFactory.createRef(path, operator, o))
}

/**
 * Creates a new instance of [PredicateBuilder] for the specified path, operator, and values with an ID.
 *
 * @param path the metamodel path representing the field to be queried
 * @param operator the operator to be used in the predicate
 * @param o the values to be used in the predicate
 * @param <T> the type of the record
 * @param <R> the type of the result
 * @param <ID> the type of the ID
 * @param <V> the type of the values
 * @return a new instance of [PredicateBuilder]
 */
fun <T : Data, R, ID, V> createWithId(
    path: Metamodel<*, V>,
    operator: Operator,
    o: Iterable<V>
): PredicateBuilder<T, R, ID> {
    return PredicateBuilderImpl(PredicateBuilderFactory.createWithId(path, operator, o))
}

/**
 * Creates a new instance of [PredicateBuilder] for the specified path, operator, and values with an ID.
 *
 * @param path the metamodel path representing the field to be queried
 * @param operator the operator to be used in the predicate
 * @param o the values to be used in the predicate
 * @param <T> the type of the record
 * @param <R> the type of the result
 * @param <ID> the type of the ID
 * @param <V> the type of the values
 * @return a new instance of [PredicateBuilder]
 */
fun <T : Data, R, ID, V : Data> createRefWithId(
    path: Metamodel<*, V>,
    operator: Operator,
    o: Iterable<Ref<V>>
): PredicateBuilder<T, R, ID> {
    return PredicateBuilderImpl(PredicateBuilderFactory.createRefWithId(path, operator, o))
}
