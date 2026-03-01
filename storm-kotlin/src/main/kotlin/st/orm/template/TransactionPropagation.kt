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
package st.orm.template

/**
 * Represents the transaction propagation behaviors that control how a transactional method interacts with an
 * existing transaction context.
 *
 * These propagation modes determine whether a new transaction is started, an existing one is joined, or
 * the method runs without a transaction. They mirror the standard Spring/JTA propagation semantics.
 *
 * Usage example:
 * ```
 * transaction(propagation = TransactionPropagation.REQUIRES_NEW) {
 *     // This always runs in a fresh transaction, even if an outer transaction exists.
 * }
 * ```
 *
 * @since 1.5
 * @see transaction
 * @see transactionBlocking
 */
enum class TransactionPropagation {

    /**
     * Join the current transaction if one exists; otherwise, create a new one.
     * This is the default propagation behavior.
     */
    REQUIRED,

    /**
     * Join the current transaction if one exists; otherwise, execute non-transactionally.
     */
    SUPPORTS,

    /**
     * Join the current transaction; throw an exception if none exists.
     */
    MANDATORY,

    /**
     * Always create a new transaction. If an outer transaction exists, it is suspended for the
     * duration of the new transaction.
     */
    REQUIRES_NEW,

    /**
     * Execute non-transactionally. If an outer transaction exists, it is suspended for the
     * duration of the execution.
     */
    NOT_SUPPORTED,

    /**
     * Execute non-transactionally; throw an exception if a transaction exists.
     */
    NEVER,

    /**
     * Execute within a nested transaction (JDBC savepoint) if an outer transaction exists;
     * otherwise, behave like [REQUIRED].
     */
    NESTED,
}
