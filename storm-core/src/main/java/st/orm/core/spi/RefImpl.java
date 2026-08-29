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

import st.orm.Data;
import st.orm.Ref;
import st.orm.core.template.impl.LazySupplier;

/**
 * Default {@link Ref} implementation.
 *
 * @param <T> record type.
 * @param <ID> primary key type.
 */
final class RefImpl<T extends Data, ID> extends AbstractRef<T, ID> {

    RefImpl(LazySupplier<T> supplier, Class<T> type, ID pk) {
        super(supplier, type, pk);
    }
}
