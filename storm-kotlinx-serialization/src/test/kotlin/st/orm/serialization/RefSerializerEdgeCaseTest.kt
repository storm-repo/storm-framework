package st.orm.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Test
import st.orm.Data
import st.orm.Entity
import st.orm.PK
import st.orm.Projection
import st.orm.Ref

/**
 * Edge case tests for [RefSerializer] covering:
 * - Non-JSON encoder/decoder error paths
 * - encodeId with null targetClass and null id
 * - decodeId fallback paths for unusual primitive types
 * - createLoadedRef with Entity vs Projection vs plain Data
 * - Error conditions in deserializeObject
 */
class RefSerializerEdgeCaseTest {

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

    // -- Non-JSON format error paths --

    @Test
    fun `RefSerializer serialize throws for non-JSON encoder`() {
        // The RefSerializer checks for JsonEncoder and throws if it's not JSON.
        // We create a RefSerializer directly and try to serialize through a non-JSON format.
        val serializer = RefSerializer(
            targetClass = SimpleEntity::class.java,
            targetSerializerProvider = { SimpleEntity.serializer() },
            refFactoryProvider = null,
        )

        // Create a custom non-JSON serialization format
        val exception = shouldThrow<SerializationException> {
            // Use a custom format that does not produce a JsonEncoder.
            NonJsonFormat.encodeToString(serializer, Ref.of(SimpleEntity::class.java, 1))
        }
        exception.message shouldContain "JSON"
    }

    @Test
    fun `RefSerializer deserialize throws for non-JSON decoder`() {
        val serializer = RefSerializer(
            targetClass = SimpleEntity::class.java,
            targetSerializerProvider = { SimpleEntity.serializer() },
            refFactoryProvider = null,
        )

        val exception = shouldThrow<SerializationException> {
            NonJsonFormat.decodeFromString(serializer, "1")
        }
        exception.message shouldContain "JSON"
    }

    // -- encodeId edge cases --

    @Serializable
    data class EntityRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `serialize null ref produces null JSON`() {
        // When ref is null, encodeId is called with null type and null id.
        val holder = EntityRefHolder(ref = null)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":null}"""
    }

    @Test
    fun `deserialize null ref`() {
        val holder = jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":null}""")
        holder.ref.shouldBeNull()
    }

    // -- decodeId fallback: double --

    @Serializable
    data class NoPkRefHolder(@Contextual val ref: Ref<NoPkData>?)

    @Test
    fun `decodeId fallback for double value on no-PK data`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":3.14}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 3.14
    }

    @Test
    fun `decodeId fallback for boolean true on no-PK data`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":true}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe true
    }

    @Test
    fun `decodeId fallback for boolean false on no-PK data`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":false}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe false
    }

    // -- decodeId error paths --

    @Test
    fun `decodeId throws for JSON object when no PK type is known`() {
        // When decoding a ref for a type without @PK, a JSON object as id should throw.
        shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":{"key":"value"}}""")
        }
    }

    @Test
    fun `decodeId throws for JSON array when no PK type is known`() {
        shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":[1,2,3]}""")
        }
    }

    // -- deserializeObject error paths --

    @Test
    fun `deserialize object without entity or projection key throws`() {
        val exception = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":{"unknown":"field"}}""")
        }
        exception.message shouldContain "@entity"
    }

    @Test
    fun `deserialize entity with null entity field throws`() {
        val exception = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":{"@entity":null}}""")
        }
        // Depending on implementation, this may throw a different error.
        exception.shouldNotBeNull()
    }

    @Test
    fun `deserialize projection without id throws`() {
        @Serializable
        data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

        val exception = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>("""{"ref":{"@projection":{"id":1,"name":"test"}}}""")
        }
        exception.message shouldContain "@id"
    }

    @Test
    fun `deserialize projection with null id throws`() {
        @Serializable
        data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

        val exception = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>("""{"ref":{"@id":null,"@projection":{"id":1,"name":"test"}}}""")
        }
        exception.message shouldContain "@id"
    }

    @Test
    fun `deserialize projection with null projection field throws`() {
        @Serializable
        data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

        shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>("""{"ref":{"@id":1,"@projection":null}}""")
        }
    }

    // -- createLoadedRef: entity vs projection vs plain data --

    @Test
    fun `createLoadedRef for entity type produces entity ref`() {
        val json = """{"ref":{"@entity":{"id":42,"name":"TestEntity"}}}"""
        val holder = jsonMapper.decodeFromString<EntityRefHolder>(json)
        holder.ref.shouldNotBeNull()
        val loaded = holder.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleEntity>()
        loaded.id shouldBe 42
        loaded.name shouldBe "TestEntity"
    }

    @Test
    fun `createLoadedRef for projection type produces projection ref`() {
        @Serializable
        data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

        val json = """{"ref":{"@id":10,"@projection":{"id":10,"name":"TestProjection"}}}"""
        val holder = jsonMapper.decodeFromString<ProjectionRefHolder>(json)
        holder.ref.shouldNotBeNull()
        val loaded = holder.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleProjection>()
        loaded.id shouldBe 10
        loaded.name shouldBe "TestProjection"
    }

    // -- encodeId with various PK types --

    @Serializable
    data class DoubleIdEntity(
        @PK val id: Double,
        val name: String,
    ) : Entity<Double>

    @Test
    fun `encodeId handles double PK type`() {
        @Serializable
        data class DoubleIdRefHolder(@Contextual val ref: Ref<DoubleIdEntity>?)

        val holder = DoubleIdRefHolder(ref = Ref.of(DoubleIdEntity::class.java, 3.14))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":3.14}"""
    }

    @Serializable
    data class BooleanIdEntity(
        @PK val id: Boolean,
        val name: String,
    ) : Entity<Boolean>

    @Test
    fun `encodeId handles boolean PK type`() {
        @Serializable
        data class BooleanIdRefHolder(@Contextual val ref: Ref<BooleanIdEntity>?)

        val holder = BooleanIdRefHolder(ref = Ref.of(BooleanIdEntity::class.java, true))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":true}"""
    }

    // -- refFactoryProvider integration --

    @Test
    fun `RefSerializer with refFactory creates refs via factory`() {
        var factoryUsed = false
        val customJson = Json {
            serializersModule = StormSerializersModule {
                object : st.orm.core.spi.RefFactory {
                    override fun <T : Data, ID : Any> create(type: Class<T>, pk: ID): Ref<T> {
                        factoryUsed = true
                        return Ref.of(type, pk)
                    }
                    override fun <T : Data, ID : Any> create(record: T, pk: ID): Ref<T> = Ref.of(record.javaClass as Class<T>, pk)
                }
            }
        }

        val holder = customJson.decodeFromString<EntityRefHolder>("""{"ref":5}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 5
        factoryUsed shouldBe true
    }

    @Test
    fun `RefSerializer with null refFactory falls back to Ref_of`() {
        val customJson = Json {
            serializersModule = StormSerializersModule { null }
        }

        val holder = customJson.decodeFromString<EntityRefHolder>("""{"ref":6}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 6
    }

    // -- Loaded entity round-trip with refFactory --

    @Test
    fun `loaded entity ref round-trip through refFactory`() {
        val customJson = Json {
            serializersModule = StormSerializersModule {
                object : st.orm.core.spi.RefFactory {
                    override fun <T : Data, ID : Any> create(type: Class<T>, pk: ID): Ref<T> = Ref.of(type, pk)
                    override fun <T : Data, ID : Any> create(record: T, pk: ID): Ref<T> = Ref.of(record.javaClass as Class<T>, pk)
                }
            }
        }

        val entity = SimpleEntity(id = 77, name = "FactoryEntity")
        val holder = EntityRefHolder(ref = Ref.of(entity))
        val json = customJson.encodeToString(holder)
        json shouldContain "@entity"

        val deserialized = customJson.decodeFromString<EntityRefHolder>(json)
        val loaded = deserialized.ref?.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 77
        loaded.name shouldBe "FactoryEntity"
    }

    // -- Float PK fallback --

    @Test
    fun `encodeId fallback for float id on no-PK data`() {
        val holder = NoPkRefHolder(ref = Ref.of(NoPkData::class.java, 1.5f))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":1.5}"""
    }

    // -- Long PK fallback --

    @Test
    fun `encodeId fallback for long id on no-PK data`() {
        val holder = NoPkRefHolder(ref = Ref.of(NoPkData::class.java, 9999999999L))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":9999999999}"""
    }

    @Test
    fun `decodeId fallback for long value on no-PK data`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":9999999999}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 9999999999L
    }
}

/**
 * A minimal encoder that is NOT a JsonEncoder, used to test RefSerializer's non-JSON error path.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class NonJsonEncoder : kotlinx.serialization.encoding.AbstractEncoder() {
    var result: String = ""

    override val serializersModule: SerializersModule = SerializersModule {}

    override fun encodeString(value: String) {
        result = value
    }

    override fun encodeInt(value: Int) {
        result = value.toString()
    }
}

/**
 * A minimal decoder that is NOT a JsonDecoder, used to test RefSerializer's non-JSON error path.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class NonJsonDecoder : kotlinx.serialization.encoding.AbstractDecoder() {
    override val serializersModule: SerializersModule = SerializersModule {}

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE

    override fun decodeString(): String = "test"
    override fun decodeInt(): Int = 1
}

/**
 * Helper object that uses non-JSON encoder/decoder directly.
 */
private object NonJsonFormat {
    fun <T> encodeToString(serializer: KSerializer<T>, value: T): String {
        val encoder = NonJsonEncoder()
        serializer.serialize(encoder, value)
        return encoder.result
    }

    fun <T> decodeFromString(deserializer: KSerializer<T>, @Suppress("UNUSED_PARAMETER") string: String): T {
        val decoder = NonJsonDecoder()
        return deserializer.deserialize(decoder)
    }
}
