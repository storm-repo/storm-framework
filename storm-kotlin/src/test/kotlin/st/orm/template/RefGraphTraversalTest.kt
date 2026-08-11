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
 * A reference that points back at the class declaring it gets that same shape; the generator breaks the cycle at a
 * navigation leaf so eager construction terminates. These are hand-written copies of the generated classes, compiled
 * against the real foundation types, so any syntax error in the emitted template is caught here. The type contract is
 * asserted at the end: the reference node is a `TypedMetamodel` (so `getResultGroupedByRef` works), while a node beyond
 * the reference is `Navigable`-only.
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
internal data class SelfNode(
    @PK val id: Int = 0,
    @FK val parent: Ref<SelfNode>? = null,
) : Entity<Int>

// Navigation-only metamodel for the type behind a self-referential reference. Its own `parent` child is the point where
// the generator breaks the cycle: it is emitted as a leaf, so eager construction terminates.
@Suppress("ClassName")
private class NavigableSelfNodeMetamodel_generated<T : st.orm.Data>(
    path: String,
    field: String,
    inline: Boolean,
    parent: Navigable<T, *>,
) : AbstractNavigableMetamodel<T, SelfNode>(SelfNode::class.java, path, field, inline, parent) {

    val id: AbstractNavigableMetamodel<T, Int>
    val parent: AbstractNavigableMetamodel<T, SelfNode>

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

        this.id = object : AbstractNavigableMetamodel<T, Int>(Int::class.javaObjectType, subPath, fieldBase + "id", false, this) {}
        this.parent = object : AbstractNavigableMetamodel<T, SelfNode>(SelfNode::class.java, subPath, fieldBase + "parent", false, this) {}
    }

    constructor(field: String, parent: Navigable<T, *>) : this("", field, false, parent)
}

// Reference metamodel for a reference declared on the type it points at. It selects the foreign key column and carries
// the same navigation children as any other reference; each hop joins the table to itself under a distinct alias.
@Suppress("ClassName")
private class SelfNodeRefMetamodel_generated<T : st.orm.Data>(
    path: String,
    field: String,
    inline: Boolean,
    parent: Metamodel<T, *>,
    private val getter: (T) -> Ref<SelfNode>?,
) : AbstractMetamodel<T, SelfNode, Ref<SelfNode>?>(SelfNode::class.java, path, field, inline, parent) {

    override fun getValue(record: T): Ref<SelfNode>? = getter(record)

    override fun isIdentical(a: T, b: T): Boolean = getter(a) === getter(b)

    override fun isSame(a: T, b: T): Boolean = getter(a) == getter(b)

    val id: AbstractNavigableMetamodel<T, Int>
    val parent: NavigableSelfNodeMetamodel_generated<T>

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

        this.id = object : AbstractNavigableMetamodel<T, Int>(Int::class.javaObjectType, subPath, fieldBase + "id", false, this) {}
        this.parent = NavigableSelfNodeMetamodel_generated(subPath, fieldBase + "parent", false, this)
    }
}

internal class RefGraphTraversalTest {

    @Test
    fun `self-referential reference metamodel constructs without recursing`() {
        // A self-referential reference metamodel constructs eagerly and addresses the foreign key column itself.
        val ref = SelfNodeRefMetamodel_generated<SelfNode>("", "parent", false, Metamodel.root(SelfNode::class.java)) { it.parent }
        ref.fieldPath() shouldBe "parent"
    }

    @Test
    fun `self-referential reference metamodel navigates beyond the reference`() {
        val ref = SelfNodeRefMetamodel_generated<SelfNode>("", "parent", false, Metamodel.root(SelfNode::class.java)) { it.parent }
        (ref as Any).shouldBeInstanceOf<TypedMetamodel<*, *, *>>()
        ref.id.fieldPath() shouldBe "parent.id"
        // The cycle is broken at a leaf, one hop further on.
        ref.parent.fieldPath() shouldBe "parent.parent"
        ref.parent.id.fieldPath() shouldBe "parent.parent.id"
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
    fun `reference node satisfies the bound that fetch requires`() {
        val animalRef: AnimalRefMetamodel_generated<Adoption> =
            AnimalRefMetamodel_generated("", "animal", false, Metamodel.root(Adoption::class.java)) { it.animal }
        // QueryBuilder.fetch takes Navigable<T, out Data>. KSP types the reference node's value as a nullable
        // Ref<Animal>? while the annotation processor types it as Ref<Animal>; only the entity type parameter is
        // constrained here, so both emitted shapes are accepted. This assignment is the compile-time proof.
        val fetchable: Navigable<Adoption, out st.orm.Data> = animalRef
        fetchable.fieldPath() shouldBe "animal"
        // A node beyond the reference that names a table satisfies the same bound, which is what makes a deeper path
        // such as User_.city.country nameable.
        val beyondTable: Navigable<Adoption, out st.orm.Data> =
            NavigableAnimalMetamodel_generated<Adoption>("animal", animalRef)
        beyondTable.fieldPath() shouldBe "animal"
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
