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

import java.util.Optional;
import st.orm.Transaction;

/**
 * Detects a transaction that an external transaction manager owns on the current thread, so that a
 * transactional block that never binds to a template still settles against it: completion callbacks the block
 * registered fire on the transaction's real outcome, and a rollback-only demand is pushed onto it.
 *
 * <p>Storm's own transactional blocks need no detection: they find each other through the transaction scope
 * chain, and a block that executes a query binds to the surrounding transaction through the template's
 * transaction provider. This SPI covers the one remaining case, where neither exists: a block that only
 * registers callbacks inside a transaction the application opened through its framework, such as Spring's
 * {@code @Transactional}.</p>
 *
 * <p>Implementations must be stateless, answering from the external manager's own thread-bound state alone.
 * They carry no configuration, and they are never consulted for query execution or transaction control, so
 * {@code ServiceLoader} discovery cannot change how templates behave; per-context configuration, such as the
 * transaction managers a template runs through, stays on the composed, instance-scoped providers. Providers
 * are asked in {@link Orderable} order and the first transaction found wins; an implementation returns an
 * empty optional whenever its transaction manager has no transaction active on the current thread.</p>
 *
 * @since 1.13
 */
public interface ExternalTransactionProvider extends Provider {

    /**
     * Returns a handle to the externally managed transaction active on the current thread, if any.
     *
     * <p>The returned handle registers callbacks with the external transaction manager, which fires them when
     * the physical transaction completes, and marks the transaction rollback-only through that manager.</p>
     *
     * @return the active transaction, or an empty optional when this provider's transaction manager has no
     * transaction active on the current thread.
     */
    Optional<Transaction> currentTransaction();
}
