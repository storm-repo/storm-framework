package st.orm.kotlin.template

import st.orm.Ref
import st.orm.kotlin.template.impl.KPredicateBuilderFactory.create
import st.orm.kotlin.template.impl.KPredicateBuilderFactory.createRef
import st.orm.template.Metamodel
import st.orm.template.Operator.*

/**
 * Infix function to create a predicate to check if a field is in a list of values.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.inList(value: Iterable<V>): KPredicateBuilder<T, T, *> =
    create(this, IN, value)

/**
 * Infix function to create a predicate to check if a field is in a list of references.
 */
inline infix fun <reified T : Record, reified V : Record> Metamodel<T, V>.inRefs(value: Iterable<Ref<V>>): KPredicateBuilder<T, T, *> =
    createRef(this, IN, value)

/**
 * Infix function to create a predicate to check if a field is not in a list of values.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.notInList(value: Iterable<V>): KPredicateBuilder<T, T, *> =
    create(this, NOT_IN, value)

/**
 * Infix function to create a predicate to check if a field is not in a list of references.
 */
inline infix fun <reified T : Record, reified V : Record> Metamodel<T, V>.notInRefs(value: Iterable<Ref<V>>): KPredicateBuilder<T, T, *> =
    createRef(this, NOT_IN, value)

/**
 * Infix functions to create a predicate to check if a field is equal to a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.eq(value: V): KPredicateBuilder<T, T, *> =
    create(this, EQUALS, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is equal to a reference.
 */
inline infix fun <reified T : Record, reified V : Record> Metamodel<T, V>.eq(value: Ref<V>): KPredicateBuilder<T, T, *> =
    createRef(this, EQUALS, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is not equal to a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.neq(value: V): KPredicateBuilder<T, T, *> =
    create(this, NOT_EQUALS, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is not equal to a reference.
 */
inline infix fun <reified T : Record, reified V : Record> Metamodel<T, V>.neq(value: Ref<V>): KPredicateBuilder<T, T, *> =
    createRef(this, NOT_EQUALS, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is like a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.like(value: V): KPredicateBuilder<T, T, *> =
    create(this, LIKE, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is not like a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.notLike(value: V): KPredicateBuilder<T, T, *> =
    create(this, NOT_LIKE, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is greater than a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.greater(value: V): KPredicateBuilder<T, T, *> =
    create(this, GREATER_THAN, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is less than a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.less(value: V): KPredicateBuilder<T, T, *> =
    create(this, LESS_THAN, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is greater than or equal to a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.greaterEq(value: V): KPredicateBuilder<T, T, *> =
    create(this, GREATER_THAN_OR_EQUAL, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is less than or equal to a value.
 */
inline infix fun <reified T : Record, reified V> Metamodel<T, V>.lessEq(value: V): KPredicateBuilder<T, T, *> =
    create(this, LESS_THAN_OR_EQUAL, listOf(value))

/**
 * Infix functions to create a predicate to check if a field is between two values.
 */
fun <T : Record, V> Metamodel<T, V>.between(left: V, right: V): KPredicateBuilder<T, T, *> =
    create(this, BETWEEN, listOf(left, right))
/**
 * Infix functions to create a predicate to check if a field is true.
 */
fun <T : Record, V> Metamodel<T, V>.isTrue(): KPredicateBuilder<T, T, *> =
    create(this, IS_TRUE, emptyList())

/**
 * Infix functions to create a predicate to check if a field is false.
 */
fun <T : Record, V> Metamodel<T, V>.isFalse(): KPredicateBuilder<T, T, *> =
    create(this, IS_FALSE, emptyList())

/**
 * Infix functions to create a predicate to check if a field is null.
 */
fun <T : Record, V> Metamodel<T, V>.isNull(): KPredicateBuilder<T, T, *> =
    create(this, IS_NULL, emptyList())

/**
 * Infix functions to create a predicate to check if a field is not null.
 */
fun <T : Record, V> Metamodel<T, V>.isNotNull(): KPredicateBuilder<T, T, *> =
    create(this, IS_NOT_NULL, emptyList())