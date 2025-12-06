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
package st.orm;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Helper class for entity operations.
 */
class EntityHelper {
    private static final Object ORM_REFLECTION;
    private static final Method GET_ID;

    static {
        try {
            try {
                Class<?> providersClass = Class.forName("st.orm.core.spi.Providers");
                Class<?> ormReflectionClass = Class.forName("st.orm.core.spi.ORMReflection");
                Method getORMReflection = providersClass.getMethod("getORMReflection");
                ORM_REFLECTION = getORMReflection.invoke(null);
                GET_ID = ormReflectionClass.getMethod("getId", Entity.class);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
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
            try {
                //noinspection unchecked
                return (ID) GET_ID.invoke(ORM_REFLECTION, entity);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}
