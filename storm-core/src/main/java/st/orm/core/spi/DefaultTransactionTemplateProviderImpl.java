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

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import st.orm.core.spi.Orderable.AfterAny;
import st.orm.core.spi.Orderable.BeforeAny;

import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

@AfterAny
public class DefaultTransactionTemplateProviderImpl implements TransactionTemplateProvider {

    @Override
    public TransactionTemplate getTransactionTemplate() {
        return new TransactionTemplate() {
            @Override
            public TransactionTemplate propagation(String propagation) {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public TransactionTemplate isolation(int isolation) {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public TransactionTemplate readOnly(boolean readOnly) {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public TransactionTemplate timeout(int timeoutSeconds) {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public TransactionContext newContext(boolean suspendMode) throws PersistenceException {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public Optional<TransactionContext> currentContext() {
                return empty();
            }

            @Override
            public ThreadLocal<TransactionContext> contextHolder() {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }

            @Override
            public <R> R execute(@Nonnull TransactionCallback<R> action, @Nonnull TransactionContext context) throws PersistenceException {
                throw new UnsupportedOperationException("Transaction template not supported.");
            }
        };
    }
}
