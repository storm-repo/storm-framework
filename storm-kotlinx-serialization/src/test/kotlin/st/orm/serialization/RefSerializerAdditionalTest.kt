package st.orm.serialization

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import st.orm.Data
import st.orm.Entity
import st.orm.PK
import st.orm.Projection
import st.orm.Ref

/**
 * Additional unit tests for [RefSerializer] covering collection scenarios (List, Set),
 * round-trip serialization, mixed loaded/unloaded refs, and edge cases not covered by
 * the primary [RefSerializerTest].
 */
class RefSerializerAdditionalTest {

    @Serializable
    data class SimpleEntity(
        @PK val id: Int = 0,
        val name: String,
    ) : Entity<Int>

    @Serializable
    data class SimpleProjection(
        @PK val id: Int = 0,
        val name: String,
    ) : Projection<Int>

    @Serializable
    data class NoPkData(
        val code: String,
        val label: String,
    ) : Data

    private val jsonMapper = Json {
        serializersModule = StormSerializers
    }

    // -- Set of refs: serialization and deserialization --

    @Serializable
    data class RefSetHolder(@Contextual val refs: Set<@Contextual Ref<SimpleEntity>>)

    @Test
    fun `serialize set of unloaded entity refs produces array of ids`() {
        val refs = setOf(
            Ref.of(SimpleEntity::class.java, 10),
            Ref.of(SimpleEntity::class.java, 20),
            Ref.of(SimpleEntity::class.java, 30),
        )
        val holder = RefSetHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[10,20,30]}"""
    }

    @Test
    fun `deserialize array of ids into set of unloaded entity refs`() {
        val holder = jsonMapper.decodeFromString<RefSetHolder>("""{"refs":[10,20,30]}""")
        holder.refs shouldHaveSize 3
        val ids = holder.refs.map { it.id() }.toSet()
        ids shouldBe setOf(10, 20, 30)
    }

    @Test
    fun `serialize set of loaded entity refs produces array of entity wrappers`() {
        val refs = setOf(
            Ref.of(SimpleEntity(id = 1, name = "Alice")),
            Ref.of(SimpleEntity(id = 2, name = "Bob")),
        )
        val holder = RefSetHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[{"@entity":{"id":1,"name":"Alice"}},{"@entity":{"id":2,"name":"Bob"}}]}"""
    }

    @Test
    fun `deserialize array of entity wrappers into set of loaded entity refs`() {
        val holder = jsonMapper.decodeFromString<RefSetHolder>(
            """{"refs":[{"@entity":{"id":1,"name":"Alice"}},{"@entity":{"id":2,"name":"Bob"}}]}""",
        )
        holder.refs shouldHaveSize 2
        val entities = holder.refs.map { it.getOrNull() }.filterNotNull().sortedBy { it.id }
        entities shouldHaveSize 2
        entities[0].name shouldBe "Alice"
        entities[1].name shouldBe "Bob"
    }

    // -- Empty list and empty set of refs --

    @Serializable
    data class RefListHolder(@Contextual val refs: List<@Contextual Ref<SimpleEntity>>)

    @Test
    fun `serialize empty list of refs produces empty array`() {
        val holder = RefListHolder(refs = emptyList())
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[]}"""
    }

    @Test
    fun `deserialize empty array into empty list of refs`() {
        val holder = jsonMapper.decodeFromString<RefListHolder>("""{"refs":[]}""")
        holder.refs.shouldBeEmpty()
    }

    @Test
    fun `serialize empty set of refs produces empty array`() {
        val holder = RefSetHolder(refs = emptySet())
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[]}"""
    }

    @Test
    fun `deserialize empty array into empty set of refs`() {
        val holder = jsonMapper.decodeFromString<RefSetHolder>("""{"refs":[]}""")
        holder.refs.shouldBeEmpty()
    }

    // -- Deserialization of list of unloaded refs --

    @Test
    fun `deserialize list of integer ids into unloaded entity refs`() {
        val holder = jsonMapper.decodeFromString<RefListHolder>("""{"refs":[5,10,15]}""")
        holder.refs shouldHaveSize 3
        holder.refs[0].id() shouldBe 5
        holder.refs[1].id() shouldBe 10
        holder.refs[2].id() shouldBe 15
        holder.refs.forEach { ref ->
            ref.getOrNull().shouldBeNull()
        }
    }

    // -- List of loaded entity refs --

    @Test
    fun `serialize list of loaded entity refs produces array of entity wrappers`() {
        val refs = listOf(
            Ref.of(SimpleEntity(id = 1, name = "Alpha")),
            Ref.of(SimpleEntity(id = 2, name = "Beta")),
            Ref.of(SimpleEntity(id = 3, name = "Gamma")),
        )
        val holder = RefListHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[{"@entity":{"id":1,"name":"Alpha"}},{"@entity":{"id":2,"name":"Beta"}},{"@entity":{"id":3,"name":"Gamma"}}]}"""
    }

    @Test
    fun `deserialize list of entity wrappers into loaded entity refs`() {
        val holder = jsonMapper.decodeFromString<RefListHolder>(
            """{"refs":[{"@entity":{"id":1,"name":"Alpha"}},{"@entity":{"id":2,"name":"Beta"}}]}""",
        )
        holder.refs shouldHaveSize 2
        val first = holder.refs[0].getOrNull()
        first.shouldNotBeNull()
        first.shouldBeInstanceOf<SimpleEntity>()
        first.id shouldBe 1
        first.name shouldBe "Alpha"
        val second = holder.refs[1].getOrNull()
        second.shouldNotBeNull()
        second.id shouldBe 2
        second.name shouldBe "Beta"
    }

    // -- Multiple ref types in same holder (entity and projection refs together) --

    @Serializable
    data class MixedRefHolder(
        @Contextual val entityRef: Ref<SimpleEntity>?,
        @Contextual val projectionRef: Ref<SimpleProjection>?,
    )

    @Test
    fun `serialize holder with both entity and projection refs unloaded`() {
        val holder = MixedRefHolder(
            entityRef = Ref.of(SimpleEntity::class.java, 42),
            projectionRef = Ref.of(SimpleProjection::class.java, 99),
        )
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"entityRef":42,"projectionRef":99}"""
    }

    @Test
    fun `deserialize holder with both entity and projection refs unloaded`() {
        val holder = jsonMapper.decodeFromString<MixedRefHolder>(
            """{"entityRef":42,"projectionRef":99}""",
        )
        holder.entityRef.shouldNotBeNull()
        holder.entityRef!!.id() shouldBe 42
        holder.entityRef!!.getOrNull().shouldBeNull()
        holder.projectionRef.shouldNotBeNull()
        holder.projectionRef!!.id() shouldBe 99
        holder.projectionRef!!.getOrNull().shouldBeNull()
    }

    @Test
    fun `serialize holder with both entity and projection refs loaded`() {
        val holder = MixedRefHolder(
            entityRef = Ref.of(SimpleEntity(id = 1, name = "EntityName")),
            projectionRef = Ref.of(SimpleProjection(id = 2, name = "ProjectionName"), 2),
        )
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"entityRef":{"@entity":{"id":1,"name":"EntityName"}},"projectionRef":{"@id":2,"@projection":{"id":2,"name":"ProjectionName"}}}"""
    }

    @Test
    fun `deserialize holder with both entity and projection refs loaded`() {
        val holder = jsonMapper.decodeFromString<MixedRefHolder>(
            """{"entityRef":{"@entity":{"id":1,"name":"EntityName"}},"projectionRef":{"@id":2,"@projection":{"id":2,"name":"ProjectionName"}}}""",
        )
        val entity = holder.entityRef?.getOrNull()
        entity.shouldNotBeNull()
        entity.shouldBeInstanceOf<SimpleEntity>()
        entity.id shouldBe 1
        entity.name shouldBe "EntityName"

        val projection = holder.projectionRef?.getOrNull()
        projection.shouldNotBeNull()
        projection.shouldBeInstanceOf<SimpleProjection>()
        projection.id shouldBe 2
        projection.name shouldBe "ProjectionName"
    }

    @Test
    fun `serialize holder with one null and one loaded ref`() {
        val holder = MixedRefHolder(
            entityRef = null,
            projectionRef = Ref.of(SimpleProjection(id = 5, name = "OnlyProjection"), 5),
        )
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"entityRef":null,"projectionRef":{"@id":5,"@projection":{"id":5,"name":"OnlyProjection"}}}"""
    }

    @Test
    fun `deserialize holder with one null and one loaded ref`() {
        val holder = jsonMapper.decodeFromString<MixedRefHolder>(
            """{"entityRef":null,"projectionRef":{"@id":5,"@projection":{"id":5,"name":"OnlyProjection"}}}""",
        )
        holder.entityRef.shouldBeNull()
        val projection = holder.projectionRef?.getOrNull()
        projection.shouldNotBeNull()
        projection.id shouldBe 5
        projection.name shouldBe "OnlyProjection"
    }

    // -- Full round-trip: entity ref --

    @Serializable
    data class EntityRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `round-trip unloaded entity ref preserves id`() {
        val original = EntityRefHolder(ref = Ref.of(SimpleEntity::class.java, 42))
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<EntityRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 42
        deserialized.ref!!.getOrNull().shouldBeNull()
    }

    @Test
    fun `round-trip loaded entity ref preserves entity data`() {
        val entity = SimpleEntity(id = 7, name = "RoundTrip")
        val original = EntityRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<EntityRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        val loaded = deserialized.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleEntity>()
        loaded.id shouldBe 7
        loaded.name shouldBe "RoundTrip"
    }

    @Test
    fun `round-trip null entity ref`() {
        val original = EntityRefHolder(ref = null)
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<EntityRefHolder>(json)
        deserialized.ref.shouldBeNull()
    }

    // -- Full round-trip: projection ref --

    @Serializable
    data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

    @Test
    fun `round-trip unloaded projection ref preserves id`() {
        val original = ProjectionRefHolder(ref = Ref.of(SimpleProjection::class.java, 99))
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<ProjectionRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 99
        deserialized.ref!!.getOrNull().shouldBeNull()
    }

    @Test
    fun `round-trip loaded projection ref preserves projection data`() {
        val projection = SimpleProjection(id = 3, name = "ProjRoundTrip")
        val original = ProjectionRefHolder(ref = Ref.of(projection, 3))
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<ProjectionRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 3
        val loaded = deserialized.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleProjection>()
        loaded.id shouldBe 3
        loaded.name shouldBe "ProjRoundTrip"
    }

    // -- Full round-trip: NoPk ref --

    @Serializable
    data class NoPkRefHolder(@Contextual val ref: Ref<NoPkData>?)

    @Test
    fun `round-trip unloaded NoPk ref with integer id`() {
        val original = NoPkRefHolder(ref = Ref.of(NoPkData::class.java, 42))
        val json = jsonMapper.encodeToString(original)
        json shouldBe """{"ref":42}"""
        val deserialized = jsonMapper.decodeFromString<NoPkRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 42
    }

    @Test
    fun `round-trip unloaded NoPk ref with string id`() {
        val original = NoPkRefHolder(ref = Ref.of(NoPkData::class.java, "abc-def"))
        val json = jsonMapper.encodeToString(original)
        json shouldBe """{"ref":"abc-def"}"""
        val deserialized = jsonMapper.decodeFromString<NoPkRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe "abc-def"
    }

    @Test
    fun `round-trip unloaded NoPk ref with long id`() {
        val original = NoPkRefHolder(ref = Ref.of(NoPkData::class.java, 9999999999L))
        val json = jsonMapper.encodeToString(original)
        json shouldBe """{"ref":9999999999}"""
        val deserialized = jsonMapper.decodeFromString<NoPkRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 9999999999L
    }

    // -- Null handling in list of refs --

    @Serializable
    data class NullableRefListHolder(@Contextual val refs: List<@Contextual Ref<SimpleEntity>?>)

    @Test
    fun `serialize list containing null ref elements`() {
        val refs = listOf(
            Ref.of(SimpleEntity::class.java, 1),
            null,
            Ref.of(SimpleEntity::class.java, 3),
        )
        val holder = NullableRefListHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[1,null,3]}"""
    }

    @Test
    fun `deserialize list containing null ref elements`() {
        val holder = jsonMapper.decodeFromString<NullableRefListHolder>("""{"refs":[1,null,3]}""")
        holder.refs shouldHaveSize 3
        holder.refs[0].shouldNotBeNull()
        holder.refs[0]!!.id() shouldBe 1
        holder.refs[1].shouldBeNull()
        holder.refs[2].shouldNotBeNull()
        holder.refs[2]!!.id() shouldBe 3
    }

    @Test
    fun `round-trip list with null ref elements`() {
        val original = NullableRefListHolder(
            refs = listOf(
                Ref.of(SimpleEntity::class.java, 10),
                null,
                Ref.of(SimpleEntity(id = 20, name = "Loaded")),
                null,
            ),
        )
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<NullableRefListHolder>(json)
        deserialized.refs shouldHaveSize 4
        deserialized.refs[0].shouldNotBeNull()
        deserialized.refs[0]!!.id() shouldBe 10
        deserialized.refs[0]!!.getOrNull().shouldBeNull()
        deserialized.refs[1].shouldBeNull()
        deserialized.refs[2].shouldNotBeNull()
        deserialized.refs[2]!!.id() shouldBe 20
        deserialized.refs[2]!!.getOrNull().shouldNotBeNull()
        deserialized.refs[2]!!.getOrNull()!!.name shouldBe "Loaded"
        deserialized.refs[3].shouldBeNull()
    }

    // -- Loaded ref round-trip: serialize loaded, deserialize, check loaded data is preserved --

    @Test
    fun `loaded entity ref round-trip preserves all entity fields`() {
        val entity = SimpleEntity(id = 100, name = "FullRoundTrip")
        val ref = Ref.of(entity)
        val holder = EntityRefHolder(ref = ref)

        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@entity":{"id":100,"name":"FullRoundTrip"}}}"""

        val deserialized = jsonMapper.decodeFromString<EntityRefHolder>(json)
        val deserializedRef = deserialized.ref
        deserializedRef.shouldNotBeNull()
        deserializedRef!!.id() shouldBe 100

        val loaded = deserializedRef.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe entity.id
        loaded.name shouldBe entity.name
    }

    @Test
    fun `loaded projection ref round-trip preserves all projection fields`() {
        val projection = SimpleProjection(id = 50, name = "ProjFullTrip")
        val ref = Ref.of(projection, 50)
        val holder = ProjectionRefHolder(ref = ref)

        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@id":50,"@projection":{"id":50,"name":"ProjFullTrip"}}}"""

        val deserialized = jsonMapper.decodeFromString<ProjectionRefHolder>(json)
        val deserializedRef = deserialized.ref
        deserializedRef.shouldNotBeNull()
        deserializedRef!!.id() shouldBe 50

        val loaded = deserializedRef.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe projection.id
        loaded.name shouldBe projection.name
    }

    // -- Mixed loaded and unloaded refs in a list --

    @Test
    fun `serialize list with mixed loaded and unloaded entity refs`() {
        val refs = listOf(
            Ref.of(SimpleEntity::class.java, 1),
            Ref.of(SimpleEntity(id = 2, name = "Loaded")),
            Ref.of(SimpleEntity::class.java, 3),
        )
        val holder = RefListHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[1,{"@entity":{"id":2,"name":"Loaded"}},3]}"""
    }

    @Test
    fun `deserialize list with mixed loaded and unloaded entity refs`() {
        val holder = jsonMapper.decodeFromString<RefListHolder>(
            """{"refs":[1,{"@entity":{"id":2,"name":"Loaded"}},3]}""",
        )
        holder.refs shouldHaveSize 3

        // First ref: unloaded, id = 1
        holder.refs[0].id() shouldBe 1
        holder.refs[0].getOrNull().shouldBeNull()

        // Second ref: loaded, id = 2
        holder.refs[1].id() shouldBe 2
        val loaded = holder.refs[1].getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleEntity>()
        loaded.name shouldBe "Loaded"

        // Third ref: unloaded, id = 3
        holder.refs[2].id() shouldBe 3
        holder.refs[2].getOrNull().shouldBeNull()
    }

    @Test
    fun `round-trip list with mixed loaded and unloaded entity refs`() {
        val original = RefListHolder(
            refs = listOf(
                Ref.of(SimpleEntity::class.java, 10),
                Ref.of(SimpleEntity(id = 20, name = "Middle")),
                Ref.of(SimpleEntity::class.java, 30),
                Ref.of(SimpleEntity(id = 40, name = "Last")),
            ),
        )
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<RefListHolder>(json)

        deserialized.refs shouldHaveSize 4
        deserialized.refs[0].id() shouldBe 10
        deserialized.refs[0].getOrNull().shouldBeNull()
        deserialized.refs[1].id() shouldBe 20
        deserialized.refs[1].getOrNull().shouldNotBeNull()
        deserialized.refs[1].getOrNull()!!.name shouldBe "Middle"
        deserialized.refs[2].id() shouldBe 30
        deserialized.refs[2].getOrNull().shouldBeNull()
        deserialized.refs[3].id() shouldBe 40
        deserialized.refs[3].getOrNull().shouldNotBeNull()
        deserialized.refs[3].getOrNull()!!.name shouldBe "Last"
    }

    // -- StormSerializers singleton basic test --

    @Test
    fun `StormSerializers singleton is not null and can create a Json instance`() {
        val json = Json {
            serializersModule = StormSerializers
        }
        val holder = EntityRefHolder(ref = Ref.of(SimpleEntity::class.java, 1))
        val encoded = json.encodeToString(holder)
        encoded shouldBe """{"ref":1}"""
    }

    @Test
    fun `StormSerializers singleton is same instance on repeated access`() {
        val first = StormSerializers
        val second = StormSerializers
        (first === second) shouldBe true
    }

    // -- Set of projection refs --

    @Serializable
    data class ProjectionRefSetHolder(@Contextual val refs: Set<@Contextual Ref<SimpleProjection>>)

    @Test
    fun `serialize set of unloaded projection refs`() {
        val refs = setOf(
            Ref.of(SimpleProjection::class.java, 1),
            Ref.of(SimpleProjection::class.java, 2),
        )
        val holder = ProjectionRefSetHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[1,2]}"""
    }

    @Test
    fun `deserialize set of unloaded projection refs`() {
        val holder = jsonMapper.decodeFromString<ProjectionRefSetHolder>("""{"refs":[1,2]}""")
        holder.refs shouldHaveSize 2
        val ids = holder.refs.map { it.id() }.toSet()
        ids shouldBe setOf(1, 2)
    }

    @Test
    fun `round-trip set of loaded projection refs`() {
        val original = ProjectionRefSetHolder(
            refs = setOf(
                Ref.of(SimpleProjection(id = 10, name = "First"), 10),
                Ref.of(SimpleProjection(id = 20, name = "Second"), 20),
            ),
        )
        val json = jsonMapper.encodeToString(original)
        val deserialized = jsonMapper.decodeFromString<ProjectionRefSetHolder>(json)
        deserialized.refs shouldHaveSize 2
        val projections = deserialized.refs.mapNotNull { it.getOrNull() }.sortedBy { it.id }
        projections shouldHaveSize 2
        projections[0].id shouldBe 10
        projections[0].name shouldBe "First"
        projections[1].id shouldBe 20
        projections[1].name shouldBe "Second"
    }

    // -- Single-element collections --

    @Test
    fun `serialize and deserialize single-element list of refs`() {
        val original = RefListHolder(refs = listOf(Ref.of(SimpleEntity(id = 1, name = "Solo"))))
        val json = jsonMapper.encodeToString(original)
        json shouldBe """{"refs":[{"@entity":{"id":1,"name":"Solo"}}]}"""
        val deserialized = jsonMapper.decodeFromString<RefListHolder>(json)
        deserialized.refs shouldHaveSize 1
        deserialized.refs[0].getOrNull().shouldNotBeNull()
        deserialized.refs[0].getOrNull()!!.name shouldBe "Solo"
    }

    @Test
    fun `serialize and deserialize single-element set of refs`() {
        val original = RefSetHolder(refs = setOf(Ref.of(SimpleEntity::class.java, 77)))
        val json = jsonMapper.encodeToString(original)
        json shouldBe """{"refs":[77]}"""
        val deserialized = jsonMapper.decodeFromString<RefSetHolder>(json)
        deserialized.refs shouldHaveSize 1
        deserialized.refs.first().id() shouldBe 77
    }
}
