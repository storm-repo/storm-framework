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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import jakarta.annotation.Nonnull;
import st.orm.Data;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.spi.RefFactory;
import st.orm.mapping.RecordField;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@SuppressWarnings("ALL")
public class RefDeserializer extends JsonDeserializer<Ref<?>>
        implements ContextualDeserializer {
    private static final ConcurrentMap<Class<?>, Class<?>> PK_CACHE = new ConcurrentHashMap<>();
    private static final ORMReflection REFLECTION = Providers.getORMReflection();
    private final JavaType wrapperType;
    private final Supplier<RefFactory> refFactorySupplier;

    public RefDeserializer(@Nonnull Supplier<RefFactory> refFactorySupplier) {
        this.refFactorySupplier = refFactorySupplier;
        this.wrapperType = null;
    }

    private RefDeserializer(@Nonnull Supplier<RefFactory> refFactorySupplier, @Nonnull JavaType wrapperType) {
        this.refFactorySupplier = refFactorySupplier;
        this.wrapperType = wrapperType;
    }

    @Override
    public Ref getNullValue(DeserializationContext ctxt) {
        return null;
    }

    @Override
    public Ref<?> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        if (wrapperType == null) {
            throw new IllegalStateException("Wrapper type must be set for RefDeserializer.");
        }
        // Grab the T from Ref<T>.
        JavaType contentType = wrapperType.containedType(0);
        Class<? extends Data> target = (Class<? extends Data>) contentType.getRawClass();
        Class<?> pkType = PK_CACHE.computeIfAbsent(target, ignore ->
                REFLECTION.getRecordType(target).fields().stream()
                        .filter(field -> field.isAnnotationPresent(PK.class))
                        .findFirst()
                        .map(RecordField::type)
                        .orElse(null)
        );
        if (pkType == null) {
            throw new PersistenceException("No primary key type found for %s.".formatted(target.getSimpleName()));
        }
        Object pk = p.readValueAs(pkType);
        return refFactorySupplier.get().create(target, pk);
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctx,
                                                BeanProperty property) {
        return new RefDeserializer(refFactorySupplier, ctx.getContextualType());
    }
}