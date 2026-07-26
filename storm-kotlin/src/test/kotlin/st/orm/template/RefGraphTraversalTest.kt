package st.orm.template

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import st.orm.AbstractMetamodel
import st.orm.AbstractNavigableMetamodel
import st.orm.Entity
import st.orm.FK
import st.orm.Metamodel
import st.orm.Navigable
import st.orm.PK
import st.orm.Ref
import st.orm.TypedMetamodel
import st.orm.template.model.Adoption
import st.orm.template.model.Animal

/**
 * Validates the exact code shape the KSP metamodel processor emits for a `Ref<X>` foreign key: a `<X>RefMetamodel`
 * (a value metamodel whose `getValue` returns the reference) whose children are navigation-only `Navigable*` nodes.
 * A reference that points back at the class declaring it is emitted as a `<X>CyclicRefMetamodel` instead, which has no
 * navigation children at all, because navigating past it would join the table to itself and resolve against the earlier
 * occurrence. These are hand-written copies of the generated classes, compiled against the real foundation types, so any
 * syntax error in the emitted template is caught here. The type contract is asserted at the end: the reference node is a
 * `TypedMetamodel` (so `getResultGroupedByRef` works), while a node beyond the reference is `Navigable`-only.
 */
@Suppress("ClassName")
private class NavigableAnimalMetamodel_generated<T : st.orm.Data>(
    path: String,
    field: String,
    inline: Boolean,
    parent: Navigable<T, *>,
) : AbstractNavigableMetamodel<T, Animal>(Animal::class.java, path, field, inline, parent) {

    val id: AbstractNavigableMetamodel<T, Int>
    val name: AbstractNavigableMetamodel<T, String>

    init {
        val subPath = if (inline) {
            path
        } else if (field.isEmpty()) {
            path
        } else if (path.isEmpty()) {
            field
        } else {
            "$path.$field"
        }
        val fieldBase = if (inline) if (field.isEmpty()) "" else "$field." else ""

        id = object : AbstractNavigableMetamodel<T, Int>(Int::class.javaObjectType, subPath, fieldBase + "id", false, this) {}
        name = object : AbstractNavigableMetamodel<T, String>(String::class.java, subPath, fieldBase + "name", false, this) {}
    }

    constructor(field: String, parent: Navigable<T, *>) : this("", field, false, parent)
}

@Suppress("ClassName")
private class AnimalRefMetamodel_generated<T : st.orm.Data>(
    path: String,
    field: String,
    inline: Boolean,
    parent: Metamodel<T, *>,
    private val getter: (T) -> Ref<Animal>?,
) : AbstractMetamodel<T, Animal, Ref<Animal>?>(Animal::class.java, path, field, inline, parent) {

    override fun getValue(record: T): Ref<Animal>? = getter(record)

    override fun isIdentical(a: T, b: T): Boolean = getter(a) === getter(b)

    override fun isSame(a: T, b: T): Boolean = getter(a) == getter(b)

    val id: AbstractNavigableMetamodel<T, Int>
    val name: AbstractNavigableMetamodel<T, String>

    init {
        val subPath = if (inline) {
            path
        } else if (field.isEmpty()) {
            path
        } else if (path.isEmpty()) {
            field
        } else {
            "$path.$field"
        }
        val fieldBase = if (inline) if (field.isEmpty()) "" else "$field." else ""

        id = object : AbstractNavigableMetamodel<T, Int>(Int::class.javaObjectType, subPath, fieldBase + "id", false, this) {}
        name = object : AbstractNavigableMetamodel<T, String>(String::class.java, subPath, fieldBase + "name", false, this) {}
    }
}

/** A self-referential entity. Its `parent` reference points back at the table that declares it. */
data class SelfNode(
    @PK val id: Int = 0,
    @FK val parent: Ref<SelfNode>? = null,
) : Entity<Int>

// Reference metamodel for a reference declared on the type it points at. It selects the foreign key column and has no
// navigation children, because navigating past it would join the table to itself and resolve against the earlier
// occurrence. The chain is walked by resolving the reference with fetch().
@Suppress("ClassName")
private class SelfNodeCyclicRefMetamodel_generated<T : st.orm.Data>(
    path: String,
    field: String,
    inline: Boolean,
    parent: Metamodel<T, *>,
    private val getter: (T) -> Ref<SelfNode>?,
) : AbstractMetamodel<T, SelfNode, Ref<SelfNode>?>(SelfNode::class.java, path, field, inline, parent) {

    override fun getValue(record: T): Ref<SelfNode>? = getter(record)

    override fun isIdentical(a: T, b: T): Boolean = getter(a) === getter(b)

    override fun isSame(a: T, b: T): Boolean = getter(a) == getter(b)
}

class RefGraphTraversalTest {

    @Test
    fun `self-referential reference metamodel constructs without recursing`() {
        // A self-referential reference metamodel constructs eagerly and addresses the foreign key column itself.
        val ref = SelfNodeCyclicRefMetamodel_generated<SelfNode>("", "parent", false, Metamodel.root(SelfNode::class.java)) { it.parent }
        ref.fieldPath() shouldBe "parent"
    }

    @Test
    fun `query api accepts a navigation-only node`() {
        val animalRef: AnimalRefMetamodel_generated<Adoption> =
            AnimalRefMetamodel_generated("", "animal", false, Metamodel.root(Adoption::class.java)) { it.animal }
        val beyond: Navigable<Adoption, String> = animalRef.name
        // Compiles only because the infix operators accept Navigable rather than requiring Metamodel. A node beyond a
        // reference is navigation-only, so this is the shape the query API must take for reference navigation to work.
        val predicate: PredicateBuilder<Adoption, Adoption, *> = beyond eq "Whiskers"
        (predicate as Any).shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    @Test
    fun `reference node is value-extractable and beyond-reference is navigation-only`() {
        val animalRef: AnimalRefMetamodel_generated<Adoption> =
            AnimalRefMetamodel_generated("", "animal", false, Metamodel.root(Adoption::class.java)) { it.animal }
        // The reference node itself is a value metamodel (so getResultGroupedByRef works).
        (animalRef as Any).shouldBeInstanceOf<TypedMetamodel<*, *, *>>()
        // Navigating beyond the reference yields a navigation-only node: Navigable but not Metamodel.
        val beyond: Navigable<Adoption, String> = animalRef.name
        (beyond as Any).shouldBeInstanceOf<Navigable<*, *>>()
        (beyond is Metamodel<*, *>).shouldBe(false)
        beyond.fieldPath() shouldBe "animal.name"
    }
}
