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
package st.orm.kotlin.spi;

import jakarta.annotation.Nonnull;
import st.orm.FK;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.kotlin.repository.KEntity;
import st.orm.spi.ORMReflection;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static st.orm.spi.Providers.getORMConverter;
import static st.orm.spi.Providers.getORMReflection;

public final class KEntityHelper {
    private static final ORMReflection REFLECTION = getORMReflection();
    private static final ConcurrentMap<Class<? extends Record>, RecordComponent> PK_CACHE = new ConcurrentHashMap<>();

    private KEntityHelper() {
    }

    public static <ID> ID getId(KEntity<ID> entity) {
        try {
            //noinspection unchecked
            return (ID) REFLECTION.invokeComponent(getPkComponent((Class<? extends Record>) entity.getClass()), entity);
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    private static RecordComponent getPkComponent(@Nonnull Class<? extends Record> componentType) {
        return PK_CACHE.computeIfAbsent(componentType, _ -> {
            var pkComponents = extractPkComponents(componentType);
            if (pkComponents.isEmpty()) {
                throw new PersistenceException(STR."No primary key found for \{componentType.getSimpleName()}.");
            }
            if (pkComponents.size() > 1) {
                throw new PersistenceException(STR."Multiple primary keys found for \{componentType.getSimpleName()}: \{pkComponents}.");
            }
            return pkComponents.getFirst();
        });
    }

    private static List<RecordComponent> extractPkComponents(@Nonnull Class<? extends Record> componentType) {
        //noinspection unchecked
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> REFLECTION.isAnnotationPresent(c, PK.class)
                        ? Stream.of(c)
                        : c.getType().isRecord() && !REFLECTION.isAnnotationPresent(c, FK.class) && getORMConverter(c).isEmpty()
                        ? extractPkComponents((Class<? extends Record>) c.getType()).stream()        // @Inline is implicitly assumed.
                        : Stream.empty()).toList();
    }
}
