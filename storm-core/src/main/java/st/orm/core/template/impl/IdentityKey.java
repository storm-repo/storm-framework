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

import jakarta.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

final class IdentityKey {
    private final Object ref;

    IdentityKey(@Nonnull Object ref) {
        this.ref = requireNonNull(ref, "ref");
    }

    @Override public boolean equals(Object o) {
        return (o instanceof IdentityKey other) && this.ref == other.ref;
    }

    @Override public int hashCode() {
        return System.identityHashCode(ref);
    }

    @Override public String toString() {
        return "IdentityKey(" + ref.getClass().getName() + "@" + Integer.toHexString(hashCode()) + ")";
    }
}
