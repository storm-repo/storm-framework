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
package st.orm.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.RefFactory;
import st.orm.mapping.RecordField;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static st.orm.core.spi.Providers.getORMReflection;

/**
 * Jackson module for serializing and deserializing Storm ORM Data types.
 *
 * <p>Handles:
 * <ul>
 *   <li>Regular Data/Entity/Projection records (standard serialization)</li>
 *   <li>Ref fields: serializes as the contained entity if loaded, or just the id if not</li>
 *   <li>Deserialization of Ref fields: creates attached Refs via RefFactory if provided,
 *       otherwise creates DetachedRefs</li>
 * </ul>
 *
 * <p>Serialization formats:
 * <ul>
 *   <li>Entity (loaded): {@code {"@entity": {...}}}</li>
 *   <li>Projection (loaded): {@code {"@id": ..., "@projection": {...}}}</li>
 *   <li>Unloaded: raw id value (e.g., {@code 42} or {@code "abc"})</li>
 * </ul>
 *
 * <p>On deserialization, raw id values use the RefFactory (if provided) to create attached Refs,
 * while object formats with {@code @entity}/{@code @projection} always create Refs via {@code Ref.of()}.
 */
public class StormModule extends SimpleModule {

    private static final String ID_FIELD = "@id";
    private static final String ENTITY_FIELD = "@entity";
    private static final String PROJECTION_FIELD = "@projection";

    private static final ORMReflection REFLECTION = getORMReflection();
    private static final Map<Class<?>, Class<?>> PK_CACHE = new ConcurrentHashMap<>();

    /**
     * Creates a StormModule without a RefFactory.
     * Ref fields with only an id will be deserialized as DetachedRefs.
     */
    public StormModule() {
        this((RefFactory) null);
    }

    /**
     * Creates a StormModule with the specified RefFactory.
     *
     * @param refFactory factory for creating attached Ref instances during deserialization,
     *                   or null to create DetachedRefs
     */
    public StormModule(@Nullable RefFactory refFactory) {
        this(refFactory != null ? () -> refFactory : null);
    }

    /**
     * Creates a StormModule with a RefFactory supplier.
     *
     * <p>This constructor is useful when the RefFactory needs to be resolved dynamically,
     * for example from a ThreadLocal, while still allowing the ObjectMapper to be cached.
     *
     * @param refFactorySupplier supplier for the RefFactory, or null to create DetachedRefs
     */
    @SuppressWarnings("unchecked")
    public StormModule(@Nullable Supplier<RefFactory> refFactorySupplier) {
        super("StormModule");
        addSerializer((Class<Ref<?>>) (Class<?>) Ref.class, new RefSerializer(null));
        addDeserializer((Class<Ref<?>>) (Class<?>) Ref.class, new RefDeserializer(refFactorySupplier, null, null));
    }

    /**
     * Resolves the primary key type for the given target class.
     *
     * @param target the entity/data class
     * @return the PK field type, or null if not found
     */
    @Nullable
    private static Class<?> resolvePkType(Class<?> target) {
        return PK_CACHE.computeIfAbsent(target, ignore ->
                REFLECTION.getRecordType(target).fields().stream()
                        .filter(field -> field.isAnnotationPresent(PK.class))
                        .findFirst()
                        .map(RecordField::type)
                        .orElse(null)
        );
    }

    /**
     * Serializer for Ref instances.
     *
     * <p>Serialization formats:
     * <ul>
     *   <li>Entity (loaded): {@code {"@entity": {...}}}</li>
     *   <li>Projection (loaded): {@code {"@id": ..., "@projection": {...}}}</li>
     *   <li>Unloaded: raw id value (e.g., {@code 42} or {@code "abc"})</li>
     * </ul>
     */
    private static class RefSerializer extends StdSerializer<Ref<?>> implements ContextualSerializer {

        @Nullable
        private final Class<?> pkType;

        RefSerializer(@Nullable Class<?> pkType) {
            super(Ref.class, false);
            this.pkType = pkType;
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov, @Nullable BeanProperty property) {
            if (property == null) {
                return this;
            }
            JavaType type = property.getType();
            JavaType refTargetType = type.containedType(0);
            if (refTargetType == null) {
                return this;
            }
            Class<?> targetClass = refTargetType.getRawClass();
            Class<?> resolvedPkType = resolvePkType(targetClass);
            return new RefSerializer(resolvedPkType);
        }

        @Override
        public void serialize(Ref<?> ref, JsonGenerator gen, SerializerProvider provider) throws IOException {
            Object loaded = ref.getOrNull();
            if (loaded != null) {
                // Loaded - serialize as object with @entity or @projection
                gen.writeStartObject();
                if (loaded instanceof Entity<?>) {
                    gen.writeFieldName(ENTITY_FIELD);
                    provider.defaultSerializeValue(loaded, gen);
                } else {
                    // Projection or other Data - include id since it can't be extracted
                    gen.writeFieldName(ID_FIELD);
                    serializeId(ref.id(), gen, provider);
                    gen.writeFieldName(PROJECTION_FIELD);
                    provider.defaultSerializeValue(loaded, gen);
                }
                gen.writeEndObject();
            } else {
                // Unloaded - serialize as raw id value
                serializeId(ref.id(), gen, provider);
            }
        }

        private void serializeId(Object id, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (id != null && pkType != null) {
                JsonSerializer<Object> serializer = provider.findValueSerializer(pkType);
                serializer.serialize(id, gen, provider);
            } else {
                provider.defaultSerializeValue(id, gen);
            }
        }
    }

    /**
     * Deserializer for Ref instances.
     *
     * <p>Handles three input formats:
     * <ul>
     *   <li>{@code {"@entity": {...}}} - Entity, uses {@code Ref.of()}</li>
     *   <li>{@code {"@id": ..., "@projection": {...}}} - Projection with id, uses {@code Ref.of()}</li>
     *   <li>Raw id value (e.g., {@code 42}) - uses RefFactory if available, otherwise {@code Ref.of()}</li>
     * </ul>
     */
    private static class RefDeserializer extends StdDeserializer<Ref<?>> implements ContextualDeserializer {

        @Nullable
        private final Supplier<RefFactory> refFactorySupplier;

        @Nullable
        private final JavaType targetType;

        @Nullable
        private final Class<?> pkType;

        RefDeserializer(@Nullable Supplier<RefFactory> refFactorySupplier,
                        @Nullable JavaType targetType,
                        @Nullable Class<?> pkType) {
            super(Ref.class);
            this.refFactorySupplier = refFactorySupplier;
            this.targetType = targetType;
            this.pkType = pkType;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, @Nullable BeanProperty property) {
            JavaType type = property != null ? property.getType() : ctxt.getContextualType();
            JavaType refTargetType = type != null ? type.containedType(0) : null;
            Class<?> resolvedPkType = null;
            if (refTargetType != null) {
                resolvedPkType = resolvePkType(refTargetType.getRawClass());
            }
            return new RefDeserializer(refFactorySupplier, refTargetType, resolvedPkType);
        }

        @Override
        @Nullable
        public Ref<?> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            if (targetType == null) {
                throw JsonMappingException.from(parser, "Cannot determine Ref target type");
            }
            @SuppressWarnings("unchecked")
            Class<Data> targetClass = (Class<Data>) targetType.getRawClass();

            return switch (parser.currentToken()) {
                case VALUE_NULL -> null;
                case START_OBJECT -> deserializeObject(parser, ctxt, targetClass);
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_STRING -> {
                    // Raw id value - use RefFactory if available
                    Object id = deserializeId(parser, ctxt);
                    yield createRefFromId(targetClass, id);
                }
                default -> throw JsonMappingException.from(
                        parser,
                        "Unexpected token " + parser.currentToken() + " when deserializing Ref"
                );
            };
        }

        private Ref<?> deserializeObject(JsonParser parser, DeserializationContext ctxt, Class<Data> targetClass)
                throws IOException {
            // Read as tree to inspect structure
            ObjectNode node = parser.readValueAsTree();

            if (node.has(ENTITY_FIELD)) {
                // Entity - deserialize and extract id from entity
                JsonNode entityNode = node.get(ENTITY_FIELD);
                Data entity = ctxt.readTreeAsValue(entityNode, targetType);
                return createLoadedRef(entity);
            } else if (node.has(PROJECTION_FIELD)) {
                // Projection - requires @id
                if (!node.has(ID_FIELD)) {
                    throw JsonMappingException.from(parser, "@projection requires @id field");
                }
                Object id = deserializeIdFromNode(node.get(ID_FIELD), ctxt);
                JsonNode projectionNode = node.get(PROJECTION_FIELD);
                Data projection = ctxt.readTreeAsValue(projectionNode, targetType);
                return createLoadedRefWithId(targetClass, projection, id);
            } else {
                throw JsonMappingException.from(parser,
                        "Ref object must contain @entity or @projection field");
            }
        }

        private Object deserializeIdFromNode(JsonNode node, DeserializationContext ctxt) throws IOException {
            if (pkType != null) {
                return ctxt.readTreeAsValue(node, pkType);
            }
            // Fallback for unknown PK type
            if (node.isInt()) {
                return node.intValue();
            } else if (node.isLong()) {
                return node.longValue();
            } else if (node.isDouble()) {
                return node.doubleValue();
            } else if (node.isTextual()) {
                return node.textValue();
            } else {
                return ctxt.readTreeAsValue(node, Object.class);
            }
        }

        private Object deserializeId(JsonParser parser, DeserializationContext ctxt) throws IOException {
            if (pkType != null) {
                return ctxt.readValue(parser, pkType);
            }
            // Fallback for unknown PK type
            return switch (parser.currentToken()) {
                case VALUE_NUMBER_INT -> {
                    long value = parser.getLongValue();
                    yield (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
                            ? (int) value
                            : value;
                }
                case VALUE_NUMBER_FLOAT -> parser.getDoubleValue();
                case VALUE_STRING -> parser.getText();
                default -> throw JsonMappingException.from(parser,
                        "Cannot deserialize id from " + parser.currentToken());
            };
        }

        @SuppressWarnings("rawtypes")
        private Ref<?> createLoadedRef(Data entity) {
            if (entity instanceof Entity<?> e) {
                return Ref.of((Entity) e);
            }
            return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Ref<?> createLoadedRefWithId(Class<Data> targetClass, Data data, Object id) {
            if (data instanceof Entity<?> e) {
                return Ref.of((Entity) e);
            } else if (data instanceof Projection<?> p) {
                return Ref.of((Projection) p, id);
            } else {
                return Ref.of(targetClass, id);
            }
        }

        private <T extends Data> Ref<T> createRefFromId(Class<T> targetClass, Object id) {
            RefFactory refFactory = refFactorySupplier != null ? refFactorySupplier.get() : null;
            if (refFactory != null) {
                return refFactory.create(targetClass, id);
            } else {
                return Ref.of(targetClass, id);
            }
        }
    }
}