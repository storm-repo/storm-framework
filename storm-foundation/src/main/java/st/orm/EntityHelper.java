/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Helper class for entity operations.
 */
class EntityHelper {
    private static final MethodHandle GET_ID;

    static {
        try {
            Class<?> providersClass = Class.forName("st.orm.core.spi.Providers");
            Class<?> ormReflectionClass = Class.forName("st.orm.core.spi.ORMReflection");
            Object ormReflection = providersClass.getMethod("getORMReflection").invoke(null);
            MethodHandle mh = MethodHandles.publicLookup().findVirtual(
                    ormReflectionClass,
                    "getId",
                    MethodType.methodType(Object.class, Data.class)
            );
            // Bind receiver once so the call site becomes (Data) -> Object.
            GET_ID = mh.bindTo(ormReflection);
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    private EntityHelper() {
        // Prevent instantiation.
    }

    /**
     * Returns the entity id.
     *
     * @param entity the entity.
     * @return the entity id.
     * @param <ID> the id type.
     */
    static <ID> ID getId(Entity<ID> entity) {
        try {
            //noinspection unchecked
            return (ID) (Object) GET_ID.invokeExact((Data) entity);
        }  catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}