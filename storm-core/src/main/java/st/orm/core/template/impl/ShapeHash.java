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

import org.jspecify.annotations.Nullable;

/**
 * Derives the 64-bit identity of a statement's shape from its shape key.
 *
 * <p>The key is a list of the template's fragments and the compilation keys of its elements. Hashing it through
 * {@link Object#hashCode()} would cap the identity at the 32 bits an {@code int} carries, so the components are
 * combined in 64-bit arithmetic instead: character content for text, elements in order for a list, and the
 * component's own hash for anything else. Keys that are equal produce the same identity, which is what lets
 * executions of one template group together.</p>
 *
 * @since 1.14
 */
final class ShapeHash {

    /** FNV-1a 64-bit offset basis and prime, used to fold each component into the running identity. */
    private static final long BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    /** Markers keep a component's own structure from colliding with a differently shaped one. */
    private static final long NULL_MARKER = 0x9e3779b97f4a7c15L;
    private static final long TEXT_MARKER = 0xff51afd7ed558ccdL;
    private static final long LIST_MARKER = 0xc4ceb9fe1a85ec53L;

    /** Stands in when the derived identity is zero, which is reserved for a shape that could not be derived. */
    private static final long NON_ZERO = 0x5bf03635a3f1c2d9L;

    private ShapeHash() {
    }

    /**
     * Returns the shape identity of the given key.
     *
     * @param key the shape key, or {@code null} when the shape could not be derived.
     * @return the identity, or {@code 0} when the key is {@code null}. Never {@code 0} otherwise.
     */
    static long of(@Nullable Object key) {
        if (key == null) {
            return 0;
        }
        long hash = avalanche(fold(BASIS, key));
        return hash == 0 ? NON_ZERO : hash;
    }

    private static long fold(long hash, @Nullable Object value) {
        return switch (value) {
            case null -> step(hash, NULL_MARKER);
            case CharSequence text -> {
                long folded = step(hash, TEXT_MARKER);
                for (int i = 0, length = text.length(); i < length; i++) {
                    folded = step(folded, text.charAt(i));
                }
                yield step(folded, text.length());
            }
            case Iterable<?> elements -> {
                long folded = step(hash, LIST_MARKER);
                int count = 0;
                for (var element : elements) {
                    folded = fold(folded, element);
                    count++;
                }
                yield step(folded, count);
            }
            // A component that carries its own equality contract folds in through it; the 32 bits it offers are
            // spread across the 64-bit identity by the components around it.
            default -> step(hash, value.hashCode());
        };
    }

    private static long step(long hash, long value) {
        return (hash ^ value) * PRIME;
    }

    /** Spreads the folded bits over the whole word, so neighbouring keys do not land in neighbouring identities. */
    private static long avalanche(long hash) {
        long spread = hash;
        spread ^= spread >>> 33;
        spread *= 0xff51afd7ed558ccdL;
        spread ^= spread >>> 33;
        spread *= 0xc4ceb9fe1a85ec53L;
        spread ^= spread >>> 33;
        return spread;
    }
}
