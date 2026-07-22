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
 * Helper class for row identity of primary key values.
 *
 * <p>Delegates to the row identity support of the core module through a method handle bound once, following the
 * same bridge as {@link EntityHelper}: an entity-typed id counts by its primary key rather than by structural
 * equality, a ref id by the key it wraps, and a composite key record by its components; scalar ids are returned
 * as-is.</p>
 */
class RowIdentityHelper {
    private static final MethodHandle NORMALIZE;

    static {
        MethodHandle normalize;
        try {
            Class<?> rowIdentityClass = Class.forName("st.orm.core.spi.RowIdentity");
            normalize = MethodHandles.publicLookup().findStatic(
                    rowIdentityClass,
                    "normalize",
                    MethodType.methodType(Object.class, Object.class)
            );
        } catch (ClassNotFoundException e) {
            // Without the core module there is no database access, so both sides of any ref comparison are
            // in-memory constructs and raw ids compare consistently.
            normalize = MethodHandles.identity(Object.class);
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
        NORMALIZE = normalize;
    }

    private RowIdentityHelper() {
        // Prevent instantiation.
    }

    /**
     * Returns the row identity of the given primary key value.
     *
     * @param id the primary key value to normalize, may be {@code null}.
     * @return the row identity of the value, or the value itself when its class requires no normalization.
     */
    static Object normalize(Object id) {
        try {
            return (Object) NORMALIZE.invokeExact(id);
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}
