package st.orm.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import st.orm.Data
import st.orm.Entity
import st.orm.PK
import st.orm.Projection
import st.orm.Ref

/**
 * Tests targeting remaining coverage gaps in StormSerializersModule.kt:
 * - resolveClassFromSerialName with nested class name patterns
 * - encodeId with known PK type and serializer (non-fallback path)
 * - decodeId with known PK type and serializer
 * - serializerOrNull returning null for unknown types
 * - createRef with null id returning null
 * - Map of refs exercising Ref in various collection positions
 */
class StormSerializersModuleCoverageTest {

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
    data class StringIdEntity(
        @PK val id: String,
        val name: String,
    ) : Entity<String>

    @Serializable
    data class LongIdEntity(
        @PK val id: Long,
        val name: String,
    ) : Entity<Long>

    @Serializable
    data class DoubleIdEntity(
        @PK val id: Double,
        val name: String,
    ) : Entity<Double>

    @Serializable
    data class NoPkData(
        val code: String,
        val label: String,
    ) : Data

    private val jsonMapper = Json {
        serializersModule = StormSerializers
    }

    // -- encodeId with known PK type exercising the non-fallback serializer path --

    @Serializable
    data class IntIdRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `encodeId uses PK type serializer for Int id`() {
        // SimpleEntity has @PK val id: Int, so PkTypeResolver resolves Int.
        // encodeId should use the serializer for Int, not the fallback.
        val holder = IntIdRefHolder(ref = Ref.of(SimpleEntity::class.java, 42))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":42}"""
    }

    @Test
    fun `decodeId uses PK type serializer for Int id`() {
        val holder = jsonMapper.decodeFromString<IntIdRefHolder>("""{"ref":99}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 99
    }

    @Serializable
    data class StringIdRefHolder(@Contextual val ref: Ref<StringIdEntity>?)

    @Test
    fun `encodeId uses PK type serializer for String id`() {
        val holder = StringIdRefHolder(ref = Ref.of(StringIdEntity::class.java, "test-123"))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":"test-123"}"""
    }

    @Test
    fun `decodeId uses PK type serializer for String id`() {
        val holder = jsonMapper.decodeFromString<StringIdRefHolder>("""{"ref":"xyz"}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe "xyz"
    }

    @Serializable
    data class LongIdRefHolder(@Contextual val ref: Ref<LongIdEntity>?)

    @Test
    fun `encodeId uses PK type serializer for Long id`() {
        val holder = LongIdRefHolder(ref = Ref.of(LongIdEntity::class.java, 9876543210L))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":9876543210}"""
    }

    @Test
    fun `decodeId uses PK type serializer for Long id`() {
        val holder = jsonMapper.decodeFromString<LongIdRefHolder>("""{"ref":9876543210}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 9876543210L
    }

    @Serializable
    data class DoubleIdRefHolder(@Contextual val ref: Ref<DoubleIdEntity>?)

    @Test
    fun `encodeId uses PK type serializer for Double id`() {
        val holder = DoubleIdRefHolder(ref = Ref.of(DoubleIdEntity::class.java, 2.718))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":2.718}"""
    }

    @Test
    fun `decodeId uses PK type serializer for Double id`() {
        val holder = jsonMapper.decodeFromString<DoubleIdRefHolder>("""{"ref":2.718}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 2.718
    }

    // -- createRef with null id --

    @Serializable
    data class NullableRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `deserialize null JSON literal for ref produces null`() {
        val holder = jsonMapper.decodeFromString<NullableRefHolder>("""{"ref":null}""")
        holder.ref.shouldBeNull()
    }

    // -- Map with Ref keys --

    @Serializable
    data class MapWithRefKeyHolder(
        @Contextual val map: Map<@Contextual Ref<SimpleEntity>, String>,
    )

    @Test
    fun `serialize map with ref keys throws SerializationException`() {
        // Ref as map key is not supported because the contextual serializer
        // cannot determine the Ref<T> target type from a map key position.
        val map = mapOf(
            Ref.of(SimpleEntity::class.java, 1) to "first",
            Ref.of(SimpleEntity::class.java, 2) to "second",
        )
        val holder = MapWithRefKeyHolder(map = map)
        shouldThrow<SerializationException> {
            jsonMapper.encodeToString(holder)
        }
    }

    // -- Map with Ref values --

    @Serializable
    data class MapWithRefValueHolder(
        @Contextual val map: Map<String, @Contextual Ref<SimpleEntity>>,
    )

    @Test
    fun `serialize map with ref values encodes refs as raw ids`() {
        val map = mapOf(
            "a" to Ref.of(SimpleEntity::class.java, 10),
            "b" to Ref.of(SimpleEntity::class.java, 20),
        )
        val holder = MapWithRefValueHolder(map = map)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"map":{"a":10,"b":20}}"""
    }

    @Test
    fun `deserialize map with ref values`() {
        val holder = jsonMapper.decodeFromString<MapWithRefValueHolder>("""{"map":{"a":10,"b":20}}""")
        holder.map["a"]!!.id() shouldBe 10
        holder.map["b"]!!.id() shouldBe 20
    }

    // -- Map with both Ref keys and Ref values --

    @Serializable
    data class MapWithBothRefHolder(
        @Contextual val map: Map<@Contextual Ref<SimpleEntity>, @Contextual Ref<LongIdEntity>>,
    )

    @Test
    fun `serialize map with both ref keys and ref values throws SerializationException`() {
        // Ref as map key is not supported because the contextual serializer
        // cannot determine the Ref<T> target type from a map key position.
        val map = mapOf(
            Ref.of(SimpleEntity::class.java, 1) to Ref.of(LongIdEntity::class.java, 100L),
        )
        val holder = MapWithBothRefHolder(map = map)
        shouldThrow<SerializationException> {
            jsonMapper.encodeToString(holder)
        }
    }

    // -- Loaded entity ref with known PK serializer path --

    @Test
    fun `serialize loaded entity ref and round-trip with known PK type`() {
        val entity = SimpleEntity(id = 55, name = "Covered")
        val holder = IntIdRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@entity":{"id":55,"name":"Covered"}}}"""

        val deserialized = jsonMapper.decodeFromString<IntIdRefHolder>(json)
        val loaded = deserialized.ref?.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 55
        loaded.name shouldBe "Covered"
    }

    // -- Loaded projection ref with known PK serializer path --

    @Serializable
    data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

    @Test
    fun `loaded projection ref round-trips preserving id and all fields`() {
        val projection = SimpleProjection(id = 33, name = "Proj")
        val holder = ProjectionRefHolder(ref = Ref.of(projection, 33))
        val json = jsonMapper.encodeToString(holder)
        // Projection refs serialize with @id (the PK) and @projection (the full object).
        json shouldBe """{"ref":{"@id":33,"@projection":{"id":33,"name":"Proj"}}}"""

        val deserialized = jsonMapper.decodeFromString<ProjectionRefHolder>(json)
        val loaded = deserialized.ref?.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 33
        loaded.name shouldBe "Proj"
    }

    // -- StormSerializersModule with custom factory exercising createRef --

    @Test
    fun `StormSerializersModule with custom refFactory that creates attached refs`() {
        var factoryInvoked = false
        val customJson = Json {
            serializersModule = StormSerializersModule {
                object : st.orm.core.spi.RefFactory {
                    override fun <T : Data, ID : Any> create(type: Class<T>, pk: ID): Ref<T> {
                        factoryInvoked = true
                        return Ref.of(type, pk)
                    }
                    override fun <T : Data, ID : Any> create(record: T, pk: ID): Ref<T> = Ref.of(record.javaClass as Class<T>, pk)
                }
            }
        }

        val holder = customJson.decodeFromString<IntIdRefHolder>("""{"ref":42}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 42
        factoryInvoked shouldBe true
    }

    // -- Nested class serial name resolution --
    // Note: this exercises resolveClassFromSerialName's nested class path
    // by using types whose serialName contains dots that need $ conversion.

    @Serializable
    data class NestedInnerEntity(
        @PK val id: Int = 0,
        val value: String,
    ) : Entity<Int>

    @Serializable
    data class NestedRefHolder(@Contextual val ref: Ref<NestedInnerEntity>?)

    @Test
    fun `nested inner class entity ref round-trips through serialization correctly`() {
        // This exercises resolveClassFromSerialName's nested class path, where the
        // serial name contains dots that must be converted to $ for inner classes.
        val entity = NestedInnerEntity(id = 7, value = "inner")
        val holder = NestedRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@entity":{"id":7,"value":"inner"}}}"""

        val deserialized = jsonMapper.decodeFromString<NestedRefHolder>(json)
        val loaded = deserialized.ref?.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 7
        loaded.value shouldBe "inner"
    }

    // -- encodeId with null ref (ref itself is null, not just id) --

    @Test
    fun `encodeId with null ref serializes to JSON null`() {
        val holder = IntIdRefHolder(ref = null)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":null}"""
    }

    // -- Unloaded projection ref serialization (exercises encodeId for projection type) --

    @Test
    fun `serialize unloaded projection ref produces raw id`() {
        val holder = ProjectionRefHolder(ref = Ref.of(SimpleProjection::class.java, 77))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":77}"""
    }

    @Test
    fun `deserialize raw id into unloaded projection ref`() {
        val holder = jsonMapper.decodeFromString<ProjectionRefHolder>("""{"ref":77}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 77
        holder.ref!!.getOrNull().shouldBeNull()
    }
}
