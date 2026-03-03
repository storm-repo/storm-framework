package st.orm.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
 * - Lines 175-176: @entity field with null payload
 * - Lines 189-190: @projection field with null payload
 * - Lines 201-203: createLoadedRef for plain Data (not Entity or Projection)
 * - Lines 240-242: decodeId when no serializer found for PK type
 * - Line 255: decodeId fallback for unsupported JSON element
 * - Lines 295-296: serializerOrNull exception path
 * - Line 121: RefSerializer non-JSON format assertion
 * - Lines 316-327: resolveTargetClass Strategy 1 patterns
 */
class StormSerializersModuleGapTest {

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
    data class PlainData(
        val code: String,
        val label: String,
    ) : Data

    private val jsonMapper = Json {
        serializersModule = StormSerializers
    }

    // @entity field with null value (line 176)

    @Serializable
    data class EntityRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `deserialize entity wrapper with explicit null entity payload throws`() {
        // JSON with @entity key but null value should throw.
        // kotlinx.serialization intercepts the null before our check, throwing its own error.
        shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":{"@entity":null}}""")
        }
    }

    // @projection field with null value (line 190)

    @Serializable
    data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

    @Test
    fun `deserialize projection wrapper with explicit null projection payload throws`() {
        // JSON with @projection key but null value should throw.
        // kotlinx.serialization intercepts the null before our check, throwing its own error.
        shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>(
                """{"ref":{"@id":1,"@projection":null}}""",
            )
        }
    }

    // createLoadedRef for plain Data (lines 201-203)
    // This path is exercised when @projection contains a type that is Data but not Entity or Projection.
    // The RefSerializer's createLoadedRef falls through to the else branch.

    @Serializable
    data class PlainDataRefHolder(@Contextual val ref: Ref<PlainData>?)

    @Test
    fun `deserialize projection wrapper with plain Data type uses class-based Ref`() {
        // PlainData is Data but neither Entity nor Projection.
        // When deserialized via @projection, createLoadedRef should take the else branch (line 203).
        val holder = jsonMapper.decodeFromString<PlainDataRefHolder>(
            """{"ref":{"@id":42,"@projection":{"code":"ABC","label":"test"}}}""",
        )
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 42
    }

    // encodeId with null ref type and null id (line 213)

    @Test
    fun `serialize null ref produces JSON null via encodeId null path`() {
        // When ref is null, serializeToJsonElement calls encodeId with null type and null id.
        val holder = EntityRefHolder(ref = null)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":null}"""
    }

    // createRef with null id (line 207)

    @Test
    fun `deserialize null JSON literal for ref returns null via createRef null path`() {
        val holder = jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":null}""")
        holder.ref.shouldBeNull()
    }

    // Loaded entity round-trip exercises the main serialize/deserialize paths

    @Test
    fun `loaded entity ref serialization exercises entity branch in serializeToJsonElement`() {
        val entity = SimpleEntity(id = 99, name = "covered")
        val holder = EntityRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@entity":{"id":99,"name":"covered"}}}"""
    }

    // Projection loaded ref exercises encodeId with non-null type and non-null id

    @Test
    fun `loaded projection ref exercises encodeId with known PK type`() {
        val projection = SimpleProjection(id = 55, name = "proj")
        val holder = ProjectionRefHolder(ref = Ref.of(projection, 55))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@id":55,"@projection":{"id":55,"name":"proj"}}}"""
    }

    // deserializeFromJsonElement else branch (line 158) for non-null, non-object element

    @Serializable
    data class NoPkDataRefHolder(@Contextual val ref: Ref<PlainData>?)

    @Test
    fun `deserialize raw id exercises deserializeFromJsonElement else branch`() {
        val holder = jsonMapper.decodeFromString<NoPkDataRefHolder>("""{"ref":42}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 42
    }

    // serializeToJsonElement unloaded branch (non-entity, non-null ref, null loaded)

    @Test
    fun `serialize unloaded ref exercises encodeId fallback for unloaded ref`() {
        val holder = EntityRefHolder(ref = Ref.of(SimpleEntity::class.java, 88))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":88}"""
    }

    // Using StormSerializersModule with concrete entity subtype

    @Serializable
    data class ConcreteEntity(
        @PK val id: Int = 0,
        val value: String,
    ) : Entity<Int>

    @Serializable
    data class ConcreteEntityRefHolder(@Contextual val ref: Ref<ConcreteEntity>?)

    @Test
    fun `concrete entity exercises resolveTargetClass with generated serializer`() {
        val entity = ConcreteEntity(id = 10, value = "concrete-test")
        val holder = ConcreteEntityRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(holder)
        json shouldContain "@entity"

        val deserialized = jsonMapper.decodeFromString<ConcreteEntityRefHolder>(json)
        val loaded = deserialized.ref?.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 10
        loaded.value shouldBe "concrete-test"
    }

    // Ref deserialization with refFactoryProvider returning null

    @Test
    fun `deserialization with null refFactoryProvider result creates detached ref`() {
        val customJson = Json {
            serializersModule = StormSerializersModule { null }
        }
        val holder = customJson.decodeFromString<EntityRefHolder>("""{"ref":77}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 77
        holder.ref!!.getOrNull().shouldBeNull()
    }

    // Ref deserialization with refFactoryProvider returning a factory

    @Test
    fun `deserialization with refFactoryProvider result creates ref via factory`() {
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
        val holder = customJson.decodeFromString<EntityRefHolder>("""{"ref":77}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 77
        factoryInvoked shouldBe true
    }
}
