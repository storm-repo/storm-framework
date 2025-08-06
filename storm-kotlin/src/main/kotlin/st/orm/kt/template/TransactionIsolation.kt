/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.kt.template

/**
 * Represents the standard transaction isolation levels supported in database transactions.
 * These levels determine how transactions interact with each other when accessing and modifying data.
 *
 * The isolation levels, from lowest to highest isolation, are:
 * 
 * - DEFAULT: Uses the default isolation level of the underlying database system
 * - READ_UNCOMMITTED: Allows transactions to read uncommitted changes from other transactions
 * - READ_COMMITTED: Only allows reading data that has been committed by other transactions
 * - REPEATABLE_READ: Ensures consistent reads of data during the transaction
 * - SERIALIZABLE: Provides the highest isolation by fully serializing transaction execution
 *
 * Usage example:
 * ```
 * transaction(isolation = TransactionIsolation.READ_COMMITTED) {
 *     // transaction code
 * }
 * ```
 * 
 * @since 1.5
 */
enum class TransactionIsolation {
    
    /**
     * The lowest isolation level. Transactions can read uncommitted changes made by other transactions.
     * This may result in dirty reads but provides the highest level of concurrency.
     * Use with caution as it can lead to inconsistent data reads.
     */
    READ_UNCOMMITTED,
    
    /**
     * Transactions can only read committed data from other transactions.
     * Prevents dirty reads but may still allow non-repeatable reads and phantom reads.
     * This is the most commonly used isolation level in practice.
     */
    READ_COMMITTED,
    
    /**
     * Ensures that if a transaction reads a record, subsequent reads of the same record will yield
     * the same value. Prevents dirty reads and non-repeatable reads but may still allow phantom reads.
     */
    REPEATABLE_READ,
    
    /**
     * The highest isolation level. Transactions are completely isolated from each other.
     * Prevents dirty reads, non-repeatable reads, and phantom reads.
     * May significantly impact performance due to increased locking.
     */
    SERIALIZABLE;
}