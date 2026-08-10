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
package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;

/**
 * Verifies that the static type-keyed caches do not pin an entity class or its class loader: after a class loaded
 * through a discardable class loader has populated the reflection, metamodel and validation caches, dropping the
 * loader must make the class collectable.
 */
class StaticCacheUnloadingTest {

    @Test
    void entityClassUnloadsAfterPopulatingCaches() throws Exception {
        WeakReference<Class<?>> reference = populateCaches();
        for (int attempt = 0; attempt < 200; attempt++) {
            if (reference.get() == null) {
                return;
            }
            System.gc();
            Thread.sleep(10);
        }
        fail("Entity class was not collected; a static cache still pins it.");
    }

    /**
     * Loads {@link UnloadableUser} a second time through a child-first loader, drives the loaded class through the
     * cache-backed entry points, and returns a weak reference to it without retaining anything else.
     */
    private WeakReference<Class<?>> populateCaches() throws Exception {
        var loader = new ChildFirstClassLoader(UnloadableUser.class.getName(), getClass().getClassLoader());
        Class<?> duplicate = Class.forName(UnloadableUser.class.getName(), true, loader);
        assertNotSame(UnloadableUser.class, duplicate);

        // Reflection caches: record type, canonical constructor, primary key field and accessors.
        ORMReflection reflection = Providers.getORMReflection();
        assertTrue(reflection.findRecordType(duplicate).isPresent());
        Object user = duplicate.getDeclaredConstructor(Integer.class, String.class).newInstance(1, "Alice");
        assertEquals(1, reflection.getId((Data) user));
        assertEquals("Alice", reflection.getRecordValue(user, 1));
        assertFalse(reflection.isDefaultValue(user));

        @SuppressWarnings("unchecked")
        var dataType = (Class<? extends Data>) duplicate;

        // Metamodel caches: root metamodel and path-based metamodel.
        assertNotNull(MetamodelFactory.root(dataType));
        assertNotNull(MetamodelFactory.of(dataType, "name"));

        // Validation and sealed pattern caches.
        assertDoesNotThrow(() -> RecordValidation.validateDataType(dataType, true));
        assertEquals(Optional.empty(), RecordReflection.detectSealedPattern(duplicate));

        return new WeakReference<>(duplicate);
    }

    /**
     * Defines the named class from its class file bytes instead of delegating to the parent, giving it a dedicated
     * loader that can be discarded. All other classes resolve through the parent as usual.
     */
    private static final class ChildFirstClassLoader extends ClassLoader {
        private final String className;

        ChildFirstClassLoader(String className, ClassLoader parent) {
            super(parent);
            this.className = className;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(className)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = readClassBytes(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] readClassBytes(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
