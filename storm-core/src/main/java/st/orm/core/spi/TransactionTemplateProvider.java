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
package st.orm.core.spi;

/**
 * The transaction template provider is responsible for integration with the Transaction subsystem.
 *
 * @since 1.5
 */
public interface TransactionTemplateProvider extends Provider {

    /**
     * Returns a transaction template.
     *
     * @return a transaction template.
     */
    TransactionTemplate getTransactionTemplate();
}
