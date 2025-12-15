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
package st.orm.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import st.orm.*
import st.orm.core.spi.ORMReflection
import st.orm.core.spi.Providers
import st.orm.core.spi.RefFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType

/**
 * The default serializers module for Storm ORM types.
 *
 * Includes contextual serialization support for [Ref] types using detached refs.
 * For attached refs (database-connected), use [StormSerializersModule] with a [RefFactory].
 *
 * Usage:
 * ```
 * val json = Json {
 *     serializersModule = StormSerializersModule
 * }
 * ```
 */
val StormSerializers: SerializersModule = StormSerializersModule()

/**
 * Creates a [SerializersModule] for Storm ORM types with optional [RefFactory] support.
 *
 * @param refFactoryProvider Optional provider for [RefFactory] to create attached refs.
 *   When null, refs are created as detached via [Ref.of].
 *   The provider is invoked on each deserialization, allowing for ThreadLocal or
 *   scoped value resolution.
 *
 * Usage:
 * ```
 * val json = Json {
 *     serializersModule = StormSerializers()
 * }
 * ```
 *
 * Usage with RefFactory:
 * ```
 * val json = Json {
 *     serializersModule = StormSerializers {
 *         // Resolve from ThreadLocal, scope, or DI container
 *         MyRefFactoryHolder.current()
 *     }
 * }
 * ```
 */
fun StormSerializersModule(
    refFactoryProvider: (() -> RefFactory?)? = null
): SerializersModule = SerializersModule {
    contextual(Ref::class) { typeArgs ->
        val targetType = typeArgs.firstOrNull()
            ?: throw SerializationException(
                "Cannot determine Ref<T> target type: missing type argument. Ensure Ref has a concrete type."
            )
        @Suppress("UNCHECKED_CAST")
        RefSerializer(
            targetClass = resolveTargetClass(targetType),
            targetSerializerProvider = { targetType as KSerializer<Data> },
            refFactoryProvider = refFactoryProvider
        )
    }
}

/**
 * Serializer for Storm ORM [Ref] types.
 *
 * Serialization formats:
 * - **Loaded Entity**: `{"@entity": {...}}`
 * - **Loaded Projection**: `{"@id": ..., "@projection": {...}}`
 * - **Unloaded**: Raw id value (e.g., `42`, `"abc"`, or object for compound PK)
 *
 * @param T The target type of the Ref
 * @param targetClass The class of the target type T (used for PK resolution)
 * @param targetSerializerProvider Lazy provider for the target serializer (only needed for loaded refs)
 * @param refFactoryProvider Optional provider for creating attached refs
 */
class RefSerializer<T : Data>(
    private val targetClass: Class<out Data>,
    targetSerializerProvider: () -> KSerializer<T>,
    private val refFactoryProvider: (() -> RefFactory?)? = null
) : KSerializer<Ref<T>> {

    // Lazy - only resolved when serializing/deserializing loaded entities
    private val targetSerializer: KSerializer<T> by lazy(targetSerializerProvider)

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("st.orm.Ref", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Ref<T>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("RefSerializer requires JSON format")

        val element = serializeToJsonElement(jsonEncoder.json, value)
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Ref<T> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("RefSerializer requires JSON format")

        val element = jsonDecoder.decodeJsonElement()
        return deserializeFromJsonElement(jsonDecoder.json, element)
    }

    private fun serializeToJsonElement(json: Json, ref: Ref<*>): JsonElement {
        val loaded = ref.getOrNull()
        return if (loaded != null) {
            buildJsonObject {
                if (loaded is Entity<*>) {
                    @Suppress("UNCHECKED_CAST")
                    put(ENTITY_FIELD, json.encodeToJsonElement(targetSerializer, loaded as T))
                } else {
                    put(ID_FIELD, encodeId(json, ref.type(), ref.id()))
                    @Suppress("UNCHECKED_CAST")
                    put(PROJECTION_FIELD, json.encodeToJsonElement(targetSerializer, loaded as T))
                }
            }
        } else {
            encodeId(json, ref.type(), ref.id())
        }
    }

    private fun deserializeFromJsonElement(json: Json, element: JsonElement): Ref<T> {
        return when (element) {
            is JsonObject -> deserializeObject(json, element)
            else -> {
                val id = decodeId(json, element, targetClass)
                @Suppress("UNCHECKED_CAST")
                createRef(targetClass as Class<Data>, id) as Ref<T>
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeObject(json: Json, obj: JsonObject): Ref<T> {
        return when {
            obj.containsKey(ENTITY_FIELD) -> {
                val payload = obj[ENTITY_FIELD]
                    ?: throw SerializationException("$ENTITY_FIELD field is null")
                val entity = json.decodeFromJsonElement(targetSerializer, payload)
                Ref.of(entity as Entity<Any?>) as Ref<T>
            }
            obj.containsKey(PROJECTION_FIELD) -> {
                val idElement = obj[ID_FIELD]
                    ?: throw SerializationException("$PROJECTION_FIELD requires $ID_FIELD field")
                val id = decodeId(json, idElement, targetClass)
                val payload = obj[PROJECTION_FIELD]!!
                val data = json.decodeFromJsonElement(targetSerializer, payload)
                createLoadedRef(data, id)
            }
            else -> throw SerializationException(
                "Ref object must contain $ENTITY_FIELD or $PROJECTION_FIELD"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createLoadedRef(data: T, id: Any): Ref<T> {
        return when (data) {
            is Entity<*> -> Ref.of(data as Entity<Any?>) as Ref<T>
            is Projection<*> -> Ref.of(data as Projection<Any?>, id) as Ref<T>
            else -> Ref.of(targetClass as Class<Data>, id) as Ref<T>
        }
    }

    private fun <D : Data> createRef(targetClass: Class<D>, id: Any): Ref<D> {
        val factory = refFactoryProvider?.invoke()
        return factory?.create(targetClass, id) ?: Ref.of(targetClass, id)
    }

    private fun encodeId(json: Json, targetClass: Class<*>, id: Any?): JsonElement {
        if (id == null) return JsonNull
        val pkType = PkTypeResolver.resolve(targetClass)
        if (pkType != null) {
            val serializer = json.serializerOrNull(pkType)
            if (serializer != null) {
                @Suppress("UNCHECKED_CAST")
                return json.encodeToJsonElement(serializer as KSerializer<Any>, id)
            }
        }
        throw SerializationException(
            "Cannot encode Ref id '$id': no serializer found for PK type '$pkType'"
        )
    }

    private fun decodeId(json: Json, element: JsonElement, targetClass: Class<*>): Any {
        val pkType = PkTypeResolver.resolve(targetClass)
            ?: throw SerializationException("Cannot decode Ref id: no PK type found for target class '$targetClass'.")
        val serializer = json.serializerOrNull(pkType)
            ?: throw SerializationException("Cannot decode Ref id: no serializer found for PK type '$pkType'.")
        return json.decodeFromJsonElement(serializer, element)
            ?: throw SerializationException("Cannot decode Ref id: null value found.")
    }

    private companion object {
        const val ID_FIELD = "@id"
        const val ENTITY_FIELD = "@entity"
        const val PROJECTION_FIELD = "@projection"
    }
}

/**
 * Cache for PK type resolution to avoid repeated reflection.
 */
@Suppress("JavaCollectionWithNullableTypeArgument")
private object PkTypeResolver {
    private val reflection: ORMReflection = Providers.getORMReflection()
    private val cache = ConcurrentHashMap<Class<*>, Class<*>?>()

    fun resolve(target: Class<*>): Class<*>? {
        return cache.computeIfAbsent(target) {
            reflection.getRecordType(target).fields()
                .firstOrNull { it.isAnnotationPresent(PK::class.java) }
                ?.type()
        }
    }
}

/**
 * Extension to get a serializer or null for a given class.
 */
private fun Json.serializerOrNull(kclass: KClass<*>): KSerializer<*>? {
    return try {
        serializersModule.serializer(kclass.starProjectedType)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun resolveTargetClass(serializer: KSerializer<*>): Class<out Data> {
    val serializerClass = serializer::class.java
    val serializerClassName = serializerClass.name
    // Strategy 1: For generated companion serializers.
    when {
        serializerClassName.endsWith("$$\$serializer") -> {
            // Nested class serializer: Outer$Inner$$serializer -> Outer$Inner.
            val targetClassName = serializerClassName.removeSuffix("$$\$serializer")
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(targetClassName, true, serializerClass.classLoader) as Class<out Data>
            } catch (_: ClassNotFoundException) {
                // Continue to next strategy.
            }
        }
        serializerClassName.endsWith("$\$serializer") -> {
            // Top-level class serializer: MyClass$serializer -> MyClass
            val targetClassName = serializerClassName.removeSuffix("$\$serializer")
            try {
                @Suppress("UNCHECKED_CAST")
                return Class.forName(targetClassName, true, serializerClass.classLoader) as Class<out Data>
            } catch (_: ClassNotFoundException) {
                // Continue to next strategy.
            }
        }
    }
    // Strategy 2: Use serialName from descriptor with nested class handling.
    val serialName = serializer.descriptor.serialName
    try {
        return resolveClassFromSerialName(serialName, serializerClass.classLoader)
    } catch (_: ClassNotFoundException) {
        // Continue.
    }
    throw SerializationException(
        "Cannot determine target class from serializer: ${serializerClass.name}, " +
                "serialName: $serialName"
    )
}

private fun resolveClassFromSerialName(name: String, classLoader: ClassLoader): Class<out Data> {
    // Try direct name first.
    try {
        @Suppress("UNCHECKED_CAST")
        return Class.forName(name, true, classLoader) as Class<out Data>
    } catch (_: ClassNotFoundException) {
        // Try converting dots to $ for nested classes, from right to left.
        val parts = name.split('.')
        for (i in parts.size - 2 downTo 1) {
            if (parts[i].firstOrNull()?.isUpperCase() == true) {
                val nestedName = parts.take(i).joinToString(".") + "$" + parts.drop(i).joinToString("$")
                try {
                    @Suppress("UNCHECKED_CAST")
                    return Class.forName(nestedName, true, classLoader) as Class<out Data>
                } catch (_: ClassNotFoundException) {
                    continue
                }
            }
        }
    }

    throw ClassNotFoundException("Could not resolve class from serialName: $name")
}

private fun Json.serializerOrNull(clazz: Class<*>): KSerializer<*>? = serializerOrNull(clazz.kotlin)