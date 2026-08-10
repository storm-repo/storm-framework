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
package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClassLoaderCacheTest {

    @Test
    void computeIfAbsentReturnsCachedValueForSameLoader() {
        var cache = new ClassLoaderCache<String>();
        var computeCount = new AtomicInteger();
        ClassLoader loader = new URLClassLoader(new URL[0]);
        assertEquals("value-1", cache.computeIfAbsent(loader, ignore -> "value-" + computeCount.incrementAndGet()));
        assertEquals("value-1", cache.computeIfAbsent(loader, ignore -> "value-" + computeCount.incrementAndGet()));
        assertEquals(1, computeCount.get());
    }

    @Test
    void computeIfAbsentDistinguishesLoadersByIdentity() {
        var cache = new ClassLoaderCache<String>();
        ClassLoader firstLoader = new URLClassLoader(new URL[0]);
        ClassLoader secondLoader = new URLClassLoader(new URL[0]);
        assertEquals("first", cache.computeIfAbsent(firstLoader, ignore -> "first"));
        assertEquals("second", cache.computeIfAbsent(secondLoader, ignore -> "second"));
    }

    @Test
    void cacheDoesNotPinCollectedLoader() throws Exception {
        var cache = new ClassLoaderCache<String>();
        ClassLoader loader = new URLClassLoader(new URL[0]);
        cache.computeIfAbsent(loader, ignore -> "value");
        var reference = new WeakReference<>(loader);
        //noinspection UnusedAssignment
        loader = null;
        awaitCleared(reference);
        // A later access drains the stale entry and the cache remains usable.
        ClassLoader otherLoader = new URLClassLoader(new URL[0]);
        assertEquals("other", cache.computeIfAbsent(otherLoader, ignore -> "other"));
    }

    private static void awaitCleared(WeakReference<?> reference) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (reference.get() == null) {
                return;
            }
            System.gc();
            Thread.sleep(10);
        }
        fail("Referent was not collected; the cache still pins it.");
    }
}
