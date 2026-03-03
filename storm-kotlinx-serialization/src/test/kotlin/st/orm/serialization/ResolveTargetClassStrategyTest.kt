package st.orm.serialization

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import st.orm.Entity
import st.orm.PK
import st.orm.Ref

/**
 * Tests that exercise resolveTargetClass Strategy 2 (serialName-based resolution)
 * and resolveClassFromSerialName (nested class dot-to-$ conversion).
 *
 * These paths are only reached when the serializer class name does NOT match the
 * generated `$$serializer` or `$$$serializer` patterns, which happens when a type
 * uses `@Serializable(with = CustomSerializer::class)`.
 */
class ResolveTargetClassStrategyTest {

    /**
     * Entity that uses a custom serializer (not the generated one).
     * The custom serializer's class name does not end with `$$serializer`,
     * forcing resolveTargetClass to use Strategy 2.
     *
     * The serialName in the descriptor is set to the fully qualified name of this
     * nested class, which includes dots that resolveClassFromSerialName must convert
     * to `$` for inner class resolution.
     */
    @Serializable(with = CustomEntityDataSerializer::class)
    data class CustomEntity(
        @PK val id: Int = 0,
        val label: String,
    ) : Entity<Int>

    @Serializable
    data class CustomEntityRefHolder(@Contextual val ref: Ref<CustomEntity>?)

    private val jsonMapper = Json {
        serializersModule = StormSerializers
    }

    @Test
    fun `custom serializer exercises resolveTargetClass Strategy 2 and nested class resolution`() {
        // CustomEntity uses @Serializable(with = CustomEntityDataSerializer::class).
        // When kotlinx.serialization resolves the serializer for Ref<CustomEntity>,
        // it passes CustomEntityDataSerializer as the type argument to the contextual block.
        // Since CustomEntityDataSerializer's class name doesn't end with $$serializer,
        // resolveTargetClass falls through Strategy 1 to Strategy 2.
        //
        // Strategy 2 uses serializer.descriptor.serialName, which is
        // "st.orm.serialization.ResolveTargetClassStrategyTest.CustomEntity".
        // resolveClassFromSerialName tries Class.forName with this name (fails because
        // inner classes use $ not .), then converts dots to $ from right to left.
        val holder = CustomEntityRefHolder(ref = Ref.of(CustomEntity::class.java, 42))
        val json = jsonMapper.encodeToString(holder)
        json shouldBe """{"ref":42}"""

        val deserialized = jsonMapper.decodeFromString<CustomEntityRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        deserialized.ref!!.id() shouldBe 42
    }

    @Test
    fun `custom serializer exercises Strategy 2 with loaded entity ref`() {
        val entity = CustomEntity(id = 7, label = "test-loaded")
        val holder = CustomEntityRefHolder(ref = Ref.of(entity))
        val json = jsonMapper.encodeToString(holder)
        json shouldContain "@entity"

        val deserialized = jsonMapper.decodeFromString<CustomEntityRefHolder>(json)
        deserialized.ref.shouldNotBeNull()
        val loaded = deserialized.ref!!.getOrNull()
        loaded.shouldNotBeNull()
        loaded.id shouldBe 7
        loaded.label shouldBe "test-loaded"
    }
}

/**
 * Custom serializer for [ResolveTargetClassStrategyTest.CustomEntity].
 *
 * This serializer's class name is "st.orm.serialization.CustomEntityDataSerializer",
 * which does NOT end with `$$serializer` or `$$$serializer`, forcing resolveTargetClass
 * to use Strategy 2 (serialName-based resolution).
 *
 * The descriptor's serialName is set to the fully qualified class name of the target type,
 * including nested class notation with dots.
 */
class CustomEntityDataSerializer : KSerializer<ResolveTargetClassStrategyTest.CustomEntity> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        // Use the serialName that includes dots for nested class reference.
        // resolveClassFromSerialName will try to resolve this, first as-is (fails),
        // then by converting dots to $ for nested classes.
        "st.orm.serialization.ResolveTargetClassStrategyTest.CustomEntity",
    ) {
        element("id", PrimitiveSerialDescriptor("id", PrimitiveKind.INT))
        element("label", PrimitiveSerialDescriptor("label", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: ResolveTargetClassStrategyTest.CustomEntity) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeIntElement(descriptor, 0, value.id)
        composite.encodeStringElement(descriptor, 1, value.label)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ResolveTargetClassStrategyTest.CustomEntity {
        val composite = decoder.beginStructure(descriptor)
        var id = 0
        var label = ""
        while (true) {
            when (val index = composite.decodeElementIndex(descriptor)) {
                0 -> id = composite.decodeIntElement(descriptor, 0)
                1 -> label = composite.decodeStringElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> break
            }
        }
        composite.endStructure(descriptor)
        return ResolveTargetClassStrategyTest.CustomEntity(id, label)
    }
}
