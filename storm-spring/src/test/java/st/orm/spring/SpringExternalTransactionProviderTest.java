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
package st.orm.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import st.orm.PersistenceException;
import st.orm.Transaction;
import st.orm.core.spi.TransactionRunner;

/**
 * Verifies the handle the external transaction provider hands out for a Spring-managed transaction: it is absent
 * without one, its callbacks follow the physical transaction's outcome, and its rollback-only state reads and
 * writes through whichever of Spring's two channels the transaction was driven by (the interceptor's bound status,
 * or the resource holders a {@code TransactionTemplate} binds).
 */
class SpringExternalTransactionProviderTest {

    /** The work a transactional proxy wraps, so the interceptor binds a transaction status for it. */
    interface Work {
        void run(Runnable body);
    }

    private final SpringExternalTransactionProvider provider = new SpringExternalTransactionProvider();
    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;
    private Work interceptedWork;

    @BeforeEach
    void setUp() {
        DataSource dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:externaltx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        // The interceptor is what @Transactional installs; a proxy over a plain object gives the same bound status
        // without a Spring context.
        var proxyFactory = new ProxyFactory((Work) Runnable::run);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new MatchAlwaysTransactionAttributeSource()));
        interceptedWork = (Work) proxyFactory.getProxy();
    }

    private Transaction currentTransaction() {
        return provider.currentTransaction().orElseThrow();
    }

    @Test
    void noHandleWithoutASpringTransaction() {
        assertTrue(provider.currentTransaction().isEmpty());
        // The runner asks the same providers, so it sees none either.
        assertNull(TransactionRunner.externalTransaction());
    }

    @Test
    void commitCallbacksFireAfterTheSpringCommitOnly() {
        List<String> events = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            Transaction transaction = currentTransaction();
            transaction.onCommit(() -> events.add("commit"));
            transaction.onRollback(() -> events.add("rollback"));
            transaction.onCompletion(committed -> events.add("completed " + committed));
            assertEquals(List.of(), events, "callbacks must wait for the physical transaction to complete");
        });
        assertEquals(List.of("commit", "completed true"), events);
    }

    @Test
    void rollbackCallbacksFireAfterTheSpringRollbackOnly() {
        List<String> events = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            Transaction transaction = currentTransaction();
            transaction.onCommit(() -> events.add("commit"));
            transaction.onRollback(() -> events.add("rollback"));
            transaction.onCompletion(committed -> events.add("completed " + committed));
            status.setRollbackOnly();
        });
        assertEquals(List.of("rollback", "completed false"), events);
    }

    @Test
    void rollbackOnlyReadsAndWritesTheResourcesATransactionTemplateBinds() {
        transactionTemplate.executeWithoutResult(status -> assertFalse(currentTransaction().isRollbackOnly()));
        // Without an interceptor status, the handle marks the resource holders the transaction manager reads when
        // it decides the outcome, so Spring's status sees the mark and the transaction rolls back. Spring reports
        // that rollback as unexpected because it did not make the mark itself.
        assertThrows(UnexpectedRollbackException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    Transaction transaction = currentTransaction();
                    transaction.setRollbackOnly();
                    assertTrue(transaction.isRollbackOnly(), "the handle reads the mark it made");
                    assertTrue(status.isRollbackOnly(), "the mark must land where Spring reads it");
                }));
    }

    @Test
    void rollbackOnlyReadsAndWritesTheStatusTheInterceptorBinds() {
        interceptedWork.run(() -> {
            Transaction transaction = currentTransaction();
            assertFalse(transaction.isRollbackOnly());
            transaction.setRollbackOnly();
            assertTrue(transaction.isRollbackOnly());
        });
        // Rolling back through the interceptor completes normally: the mark went onto the interceptor's status,
        // which is Spring's own channel, so no unexpected rollback is reported.
        List<String> events = new ArrayList<>();
        interceptedWork.run(() -> {
            currentTransaction().onRollback(() -> events.add("rollback"));
            currentTransaction().setRollbackOnly();
        });
        assertEquals(List.of("rollback"), events);
    }

    @Test
    void aSynchronizationOnlyTransactionCannotBeMarkedRollbackOnly() {
        // Synchronization is active but no resource transaction was driven: there is nothing to mark, and the
        // provider says so rather than pretending the mark took.
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            Transaction transaction = currentTransaction();
            assertFalse(transaction.isRollbackOnly());
            PersistenceException exception = assertThrows(PersistenceException.class, transaction::setRollbackOnly);
            assertTrue(exception.getMessage().contains("holds no resource to mark rollback-only"), exception.getMessage());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
