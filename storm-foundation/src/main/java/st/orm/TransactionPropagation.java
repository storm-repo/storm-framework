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

/**
 * Defines how a transaction block relates to a transaction that is already active when the block starts.
 *
 * <p>The semantics mirror the standard transaction propagation behaviors. The default is {@link #REQUIRED}.</p>
 *
 * @since 1.13
 */
public enum TransactionPropagation {

    /**
     * Joins the active transaction, or starts a new one when none is active. The default.
     */
    REQUIRED,

    /**
     * Joins the active transaction when one is active; otherwise runs without a transaction.
     */
    SUPPORTS,

    /**
     * Joins the active transaction; fails when none is active.
     */
    MANDATORY,

    /**
     * Always starts a new, independent transaction, suspending the active one for the duration of the block.
     */
    REQUIRES_NEW,

    /**
     * Runs without a transaction, suspending the active one for the duration of the block.
     */
    NOT_SUPPORTED,

    /**
     * Runs without a transaction; fails when one is active.
     */
    NEVER,

    /**
     * Runs within a nested transaction (a savepoint) when a transaction is active; otherwise starts a new one.
     */
    NESTED
}
