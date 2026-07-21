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
package st.orm.mapping;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Constructs record instances without reflection.
 *
 * <p>The metamodel generators emit an implementation per record type that invokes the record's canonical
 * constructor (the primary constructor for Kotlin data classes) directly, registered through
 * {@code META-INF/services/st.orm.mapping.Instantiator}. The row mapper dispatches to a registered instantiator
 * instead of {@code Constructor.newInstance}, so applications run without reflective construction: no reflection
 * configuration for native images, and no {@code opens} clauses for modular applications.</p>
 *
 * <p>When no instantiator is registered for a type, the row mapper falls back to reflective construction, so
 * models compiled without the generators keep working unchanged.</p>
 *
 * @param <T> the record type this instantiator constructs.
 * @since 1.13
 */
public interface Instantiator<T> {

    /**
     * Returns the record type this instantiator constructs.
     *
     * @return the constructed record type.
     */
    Class<T> type();

    /**
     * Constructs a new instance from the canonical constructor arguments.
     *
     * <p>The arguments are positional and fully adapted: the caller has already performed null checks and type
     * conversion, so implementations only cast and invoke the constructor.</p>
     *
     * @param args the canonical constructor arguments, in declaration order.
     * @return the constructed instance.
     */
    T instantiate(@Nonnull Object[] args);

    /**
     * Deconstructs the given instance into its canonical constructor arguments, in declaration order.
     *
     * <p>Generated instantiators override this to read the components directly, completing the reflection-free
     * round trip for record rebuilds: component reads run as generated code, matching {@link #instantiate} on the
     * construction side. The returned array is freshly allocated; callers may modify it and pass it to
     * {@link #instantiate} to build an adjusted copy of the instance.</p>
     *
     * <p>The default returns {@code null}, signalling that no generated deconstructor is available; callers fall
     * back to reflective component access.</p>
     *
     * @param instance the instance to deconstruct.
     * @return the component values in declaration order, or {@code null} when not supported.
     * @since 1.13
     */
    @Nullable
    default Object[] deconstruct(@Nonnull T instance) {
        return null;
    }
}
