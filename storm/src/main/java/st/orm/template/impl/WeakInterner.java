/*
 * Copyright 2024 the original author or authors.
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
package st.orm.template.impl;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * A weak interner that allows fast lookups and retrieval of existing instances based on equality, while holding
 * elements weakly to permit garbage collection.
 */
class WeakInterner {
    private final Map<Object, WeakReference<Object>> map = new WeakHashMap<>();

    /**
     * Interns the given object, ensuring that only one canonical instance exists. If an equivalent object is already
     * present, returns the existing instance. Otherwise, adds the new object to the interner and returns it.
     *
     * @param obj The object to intern.
     * @return the canonical instance of the object.
     */
    public <T> T intern(T obj) {
        Objects.requireNonNull(obj, "Cannot intern null object.");
        // Check if an equivalent object already exists.
        WeakReference<Object> existing = map.get(obj);
        if (existing != null) {
            // Equivalent object found; return existing instance
            var result = existing.get();
            if (result != null) {
                if (result.getClass() != obj.getClass()) {
                    throw new IllegalArgumentException("Cannot intern objects of different classes.");
                }
                //noinspection unchecked
                return (T) result;
            }
            return obj;
        }
        map.put(obj, new WeakReference<>(obj));
        return obj;
    }

    /**
     * Returns the number of interned objects currently in the interner.
     *
     * @return The number of interned objects.
     */
    public int size() {
        return map.size();
    }

    /**
     * Clears all interned objects from the interner.
     */
    public void clear() {
        map.clear();
    }
}