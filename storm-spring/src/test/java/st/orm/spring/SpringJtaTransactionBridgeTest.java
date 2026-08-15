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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.TransactionIsolation.SERIALIZABLE;
import static st.orm.TransactionPropagation.NESTED;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.transaction;

import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import st.orm.TransactionOptions;
import st.orm.repository.EntityRepository;
import st.orm.spring.model.Visit;
import st.orm.template.ORMTemplate;
import st.orm.template.Transactions;

/**
 * Verifies that Storm's programmatic transaction API ({@link Transactions}) is bridged into a
 * {@link JtaTransactionManager}. A JTA manager owns no single data source, so it is matched only when no
 * resource-bound manager claims the one Storm is using.
 *
 * <p>The tests assert which calls Storm drives on the {@code UserTransaction}, not what the database does
 * with them. Enlisting a connection in a global transaction is the job of an XA-aware pool and a real
 * transaction manager, neither of which is in play here; the recording implementation below stands in for
 * the transaction manager an application server provides.</p>
 */
class SpringJtaTransactionBridgeTest {

    private DataSource dataSource;
    private RecordingUserTransaction userTransaction;
    private JtaTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:jtabridgetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        userTransaction = new RecordingUserTransaction();
        transactionManager = new JtaTransactionManager(userTransaction);
    }

    private EntityRepository<Visit, Integer> visits(PlatformTransactionManager... managers) {
        ORMTemplate orm = SpringOrmTemplate.of(dataSource, () -> List.of(managers));
        return orm.entity(Visit.class);
    }

    @Test
    void programmaticTransactionDrivesTheJtaManager() {
        var repository = visits(transactionManager);
        transaction(tx -> {
            repository.count();
            return null;
        });
        assertEquals(List.of("begin", "commit"), userTransaction.calls);
    }

    @Test
    void rollbackOnlyDrivesTheJtaManager() {
        var repository = visits(transactionManager);
        transaction(tx -> {
            repository.count();
            tx.setRollbackOnly();
            return null;
        });
        assertEquals(List.of("begin", "rollback"), userTransaction.calls);
    }

    @Test
    void resourceBoundManagerWinsOverTheJtaManager() {
        var repository = visits(transactionManager, new DataSourceTransactionManager(dataSource));
        transaction(tx -> {
            repository.count();
            return null;
        });
        // A JTA manager owns every data source, so matching it alongside a resource-bound one would report
        // ambiguity on every application that configures both. The resource-bound manager takes the transaction.
        assertEquals(List.of(), userTransaction.calls);
    }

    @Test
    void customIsolationUnderJtaNamesTheOption() {
        var repository = visits(transactionManager);
        var options = TransactionOptions.defaults().withIsolation(SERIALIZABLE);
        Exception exception = assertThrows(Exception.class, () ->
                transaction(options, tx -> {
                    repository.count();
                    return null;
                }));
        String message = messageChain(exception);
        assertTrue(message.contains("does not support SERIALIZABLE isolation"), message);
        assertTrue(message.contains("allow custom isolation levels"), message);
    }

    @Test
    void nestedPropagationUnderJtaNamesTheOption() {
        var repository = visits(transactionManager);
        var nested = TransactionOptions.defaults().withPropagation(NESTED);
        Exception exception = assertThrows(Exception.class, () ->
                transaction(outer -> {
                    repository.count();
                    return transaction(nested, inner -> {
                        repository.count();
                        return null;
                    });
                }));
        String message = messageChain(exception);
        assertTrue(message.contains("does not support NESTED propagation"), message);
    }

    @Test
    void requiresNewUnderJtaNamesTheOption() {
        var repository = visits(transactionManager);
        Exception exception = assertThrows(Exception.class, () ->
                transaction(outer -> {
                    repository.count();
                    return transaction(REQUIRES_NEW, inner -> {
                        repository.count();
                        return null;
                    });
                }));
        // A manager built from a UserTransaction alone cannot suspend, which REQUIRES_NEW needs when a
        // transaction is active. The refusal names the propagation, not Spring's suspension exception.
        String message = messageChain(exception);
        assertTrue(message.contains("cannot suspend the surrounding transaction"), message);
        assertTrue(message.contains("REQUIRES NEW propagation"), message);
    }

    private static String messageChain(Throwable throwable) {
        var builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            builder.append(current.getMessage()).append('\n');
        }
        return builder.toString();
    }

    /**
     * Stands in for the {@code UserTransaction} an application server provides, recording what Storm drives
     * through it. {@link #begin()} refuses to nest, which is what the JTA specification requires and what
     * makes {@code JtaTransactionManager} report nesting as unsupported.
     */
    private static final class RecordingUserTransaction implements UserTransaction {

        private final List<String> calls = new ArrayList<>();

        private int status = Status.STATUS_NO_TRANSACTION;

        @Override
        public void begin() throws NotSupportedException {
            if (status != Status.STATUS_NO_TRANSACTION) {
                throw new NotSupportedException("A transaction is already active on this thread.");
            }
            calls.add("begin");
            status = Status.STATUS_ACTIVE;
        }

        @Override
        public void commit() {
            calls.add("commit");
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void rollback() {
            calls.add("rollback");
            status = Status.STATUS_NO_TRANSACTION;
        }

        @Override
        public void setRollbackOnly() {
            calls.add("setRollbackOnly");
            status = Status.STATUS_MARKED_ROLLBACK;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void setTransactionTimeout(int seconds) {
            calls.add("setTransactionTimeout(" + seconds + ")");
        }
    }
}
