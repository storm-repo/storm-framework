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

import static java.lang.System.identityHashCode;
import static java.util.Objects.requireNonNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A cache scoped to a class loader, holding the loader weakly and the value softly.
 *
 * <p>Values computed for a class loader inherently reference that loader: service instances and loaded classes keep
 * their defining loader reachable. A cache that held such a value strongly would pin the loader for the lifetime of
 * the JVM, which leaks every redeployed application in a container that discards class loaders. Holding the value
 * softly breaks the cycle: once a loader is otherwise unreachable, the cache entry is the only path to it, and the
 * collector clears the soft reference before memory runs out, after which the loader and its classes are reclaimed.
 * Entries of live loaders survive until memory pressure clears them, in which case the value is recomputed on the
 * next access.</p>
 *
 * <p>Loaders are compared by identity. Stale keys are drained from a reference queue on each access.</p>
 *
 * @param <V> the type of the cached value; must not be {@code null}.
 */
final class ClassLoaderCache<V> {

    /** A weak reference to a class loader with identity-based equality, usable as a map key. */
    private static final class LoaderKey extends WeakReference<ClassLoader> {
        private final int hash;

        /** Creates a lookup key that is not registered with a reference queue. */
        LoaderKey(ClassLoader loader) {
            super(loader);
            this.hash = identityHashCode(loader);
        }

        /** Creates a key for insertion, registered with the queue for cleanup when the loader is collected. */
        LoaderKey(ClassLoader loader, ReferenceQueue<ClassLoader> queue) {
            super(loader, queue);
            this.hash = identityHashCode(loader);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                // Required to locate this key after its referent is cleared, so a stale entry can be removed.
                return true;
            }
            return other instanceof LoaderKey key && get() != null && get() == key.get();
        }
    }

    private final ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
    private final Map<LoaderKey, SoftReference<V>> map = new ConcurrentHashMap<>();

    /**
     * Returns the value for the given class loader, computing and caching it if absent or already reclaimed.
     *
     * <p>The compute function may run while holding an internal lock for the loader's entry; concurrent lookups of
     * the same loader wait for the computation, matching {@link ConcurrentHashMap#computeIfAbsent} semantics.</p>
     *
     * @param loader the class loader to scope the value to.
     * @param compute the function that computes the value; must not return {@code null}.
     * @return the cached or computed value.
     */
    V computeIfAbsent(ClassLoader loader, Function<? super ClassLoader, ? extends V> compute) {
        drainQueue();
        var reference = map.get(new LoaderKey(loader));
        V value = reference == null ? null : reference.get();
        while (value == null) {
            var updated = map.compute(new LoaderKey(loader, queue), (key, existing) ->
                    existing != null && existing.get() != null
                            ? existing
                            : new SoftReference<>(requireNonNull(compute.apply(loader),
                                    "Compute function must not return null.")));
            value = updated.get();
        }
        return value;
    }

    /**
     * Removes stale entries whose class loader has been collected. Only the exact enqueued key matches its map
     * entry, so a live entry for another loader is never removed.
     */
    private void drainQueue() {
        Reference<? extends ClassLoader> stale;
        while ((stale = queue.poll()) != null) {
            if (stale instanceof LoaderKey key) {
                map.remove(key);
            }
        }
    }
}
