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
 * Interface for programmatic transaction management with ORMTemplate.
 *
 * Provides methods to check if the transaction is rollback-only and to set it as such.
 * This interface extends ORMTemplate to allow access to ORM operations within a transaction context.
 *
 * @property isRollbackOnly Indicates if the transaction is marked for rollback only.
 * @property setRollbackOnly Marks the transaction as rollback-only.
 * @since 1.5
 */
interface Transaction {

    /**
     * Indicates if the transaction is marked for rollback only.
     */
    val isRollbackOnly: Boolean

    /**
     * Marks the transaction as rollback-only, preventing any further commits.
     */
    fun setRollbackOnly()
}