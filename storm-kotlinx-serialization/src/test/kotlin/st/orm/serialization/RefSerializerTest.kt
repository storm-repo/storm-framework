package st.orm.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
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
 * Unit tests for [RefSerializer] and [StormSerializersModule] covering edge cases
 * and error paths not exercised by the integration tests.
 */
class RefSerializerTest {

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

    // A Data type with no @PK field, causing PkTypeResolver to return null
    // and triggering the fallback encoding/decoding paths.
    @Serializable
    data class NoPkData(
        val code: String,
        val label: String,
    ) : Data

    private val jsonMapper = Json {
        serializersModule = StormSerializers
    }

    // -- Null and basic Ref serialization --

    @Serializable
    data class EntityRefHolder(@Contextual val ref: Ref<SimpleEntity>?)

    @Test
    fun `serialize null ref produces null`() {
        val holder = EntityRefHolder(ref = null)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":null}"""
    }

    @Test
    fun `deserialize null ref`() {
        val holder = jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":null}""")
        holder.ref.shouldBeNull()
    }

    @Test
    fun `serialize unloaded entity ref produces raw id`() {
        val ref = Ref.of(SimpleEntity::class.java, 42)
        val holder = EntityRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":42}"""
    }

    @Test
    fun `deserialize raw integer id into unloaded ref`() {
        val holder = jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":42}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 42
    }

    @Test
    fun `serialize loaded entity ref produces entity wrapper`() {
        val entity = SimpleEntity(id = 7, name = "test")
        val ref = Ref.of(entity)
        val holder = EntityRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@entity":{"id":7,"name":"test"}}}"""
    }

    @Test
    fun `deserialize entity wrapper back to loaded ref`() {
        val holder = jsonMapper.decodeFromString<EntityRefHolder>(
            """{"ref":{"@entity":{"id":7,"name":"test"}}}""",
        )
        holder.ref.shouldNotBeNull()
        val loaded = holder.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleEntity>()
        loaded.id shouldBe 7
        loaded.name shouldBe "test"
    }

    // -- Projection ref serialization --

    @Serializable
    data class ProjectionRefHolder(@Contextual val ref: Ref<SimpleProjection>?)

    @Test
    fun `serialize loaded projection ref produces projection wrapper with id`() {
        val proj = SimpleProjection(id = 3, name = "proj")
        val ref = Ref.of(proj, 3)
        val holder = ProjectionRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":{"@id":3,"@projection":{"id":3,"name":"proj"}}}"""
    }

    @Test
    fun `deserialize projection wrapper back to loaded ref`() {
        val holder = jsonMapper.decodeFromString<ProjectionRefHolder>(
            """{"ref":{"@id":3,"@projection":{"id":3,"name":"proj"}}}""",
        )
        holder.ref.shouldNotBeNull()
        val loaded = holder.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.shouldBeInstanceOf<SimpleProjection>()
        loaded.id shouldBe 3
        loaded.name shouldBe "proj"
    }

    // -- Deserialization error paths in deserializeObject --

    @Test
    fun `deserialize object without entity or projection field throws`() {
        val ex = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<EntityRefHolder>("""{"ref":{"unknown":"value"}}""")
        }
        ex.message shouldContain "@entity"
    }

    @Test
    fun `deserialize entity where payload is not an entity throws`() {
        // SimpleProjection is not an Entity, so decoding as @entity should fail.
        val ex = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>(
                """{"ref":{"@entity":{"id":1,"name":"x"}}}""",
            )
        }
        ex.message shouldContain "@entity"
    }

    @Test
    fun `deserialize projection without id field throws`() {
        val ex = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>(
                """{"ref":{"@projection":{"id":1,"name":"x"}}}""",
            )
        }
        ex.message shouldContain "@id"
    }

    @Test
    fun `deserialize projection with null id throws`() {
        val ex = shouldThrow<SerializationException> {
            jsonMapper.decodeFromString<ProjectionRefHolder>(
                """{"ref":{"@id":null,"@projection":{"id":1,"name":"x"}}}""",
            )
        }
        ex.message shouldContain "@id"
    }

    // -- String ID entity (covers String primitive encoding/decoding fallback) --

    @Serializable
    data class StringIdRefHolder(@Contextual val ref: Ref<StringIdEntity>?)

    @Test
    fun `serialize unloaded ref with string id`() {
        val ref = Ref.of(StringIdEntity::class.java, "abc-123")
        val holder = StringIdRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":"abc-123"}"""
    }

    @Test
    fun `deserialize string id ref`() {
        val holder = jsonMapper.decodeFromString<StringIdRefHolder>("""{"ref":"abc-123"}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe "abc-123"
    }

    // -- Long ID entity --

    @Serializable
    data class LongIdRefHolder(@Contextual val ref: Ref<LongIdEntity>?)

    @Test
    fun `serialize unloaded ref with long id`() {
        val ref = Ref.of(LongIdEntity::class.java, 9999999999L)
        val holder = LongIdRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":9999999999}"""
    }

    @Test
    fun `deserialize long id ref`() {
        val holder = jsonMapper.decodeFromString<LongIdRefHolder>("""{"ref":9999999999}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 9999999999L
    }

    // -- NoPkData (triggers fallback paths in encodeId/decodeId) --

    @Serializable
    data class NoPkRefHolder(@Contextual val ref: Ref<NoPkData>?)

    @Test
    fun `serialize unloaded ref for NoPk data with int id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, 42)
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":42}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with string id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, "abc")
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":"abc"}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with long id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, 9999999999L)
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":9999999999}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with double id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, 3.14)
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":3.14}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with float id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, 2.5f)
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":2.5}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with boolean id triggers fallback`() {
        val ref = Ref.of(NoPkData::class.java, true)
        val holder = NoPkRefHolder(ref = ref)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":true}"""
    }

    @Test
    fun `serialize unloaded ref for NoPk data with unsupported id type throws`() {
        val ref = Ref.of(NoPkData::class.java, listOf(1, 2, 3))
        val holder = NoPkRefHolder(ref = ref)
        shouldThrow<SerializationException> {
            jsonMapper.encodeToString(holder)
        }
    }

    @Test
    fun `deserialize NoPk ref with int fallback`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":42}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 42
    }

    @Test
    fun `deserialize NoPk ref with string fallback`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":"abc"}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe "abc"
    }

    @Test
    fun `deserialize NoPk ref with long fallback`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":9999999999}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 9999999999L
    }

    @Test
    fun `deserialize NoPk ref with boolean fallback`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":true}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe true
    }

    @Test
    fun `deserialize NoPk ref with double fallback`() {
        val holder = jsonMapper.decodeFromString<NoPkRefHolder>("""{"ref":3.14}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 3.14
    }

    // -- StormSerializersModule with refFactoryProvider --

    @Test
    fun `StormSerializersModule with refFactoryProvider uses factory for deserialization`() {
        val factoryJson = Json {
            serializersModule = StormSerializersModule {
                // Return null factory - falls back to Ref.of
                null
            }
        }

        @Serializable
        data class FactoryHolder(@Contextual val ref: Ref<SimpleEntity>?)

        val holder = factoryJson.decodeFromString<FactoryHolder>("""{"ref":10}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 10
    }

    @Test
    fun `StormSerializersModule with non-null refFactoryProvider`() {
        val factoryJson = Json {
            serializersModule = StormSerializersModule {
                // Return a simple factory that creates detached refs
                object : st.orm.core.spi.RefFactory {
                    override fun <T : Data, ID : Any> create(type: Class<T>, pk: ID): Ref<T> = Ref.of(type, pk)
                    override fun <T : Data, ID : Any> create(record: T, pk: ID): Ref<T> = Ref.of(record.javaClass as Class<T>, pk)
                }
            }
        }

        @Serializable
        data class FactoryHolder2(@Contextual val ref: Ref<SimpleEntity>?)

        val holder = factoryJson.decodeFromString<FactoryHolder2>("""{"ref":5}""")
        holder.ref.shouldNotBeNull()
        holder.ref!!.id() shouldBe 5
    }

    // -- List of refs --

    @Serializable
    data class RefListHolder(@Contextual val refs: List<@Contextual Ref<SimpleEntity>>)

    @Test
    fun `serialize list of unloaded refs`() {
        val refs = listOf(
            Ref.of(SimpleEntity::class.java, 1),
            Ref.of(SimpleEntity::class.java, 2),
        )
        val holder = RefListHolder(refs = refs)
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"refs":[1,2]}"""
    }
}
