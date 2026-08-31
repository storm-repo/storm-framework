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
import static st.orm.TransactionPropagation.NESTED;
import static st.orm.TransactionPropagation.NEVER;
import static st.orm.TransactionPropagation.NOT_SUPPORTED;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.transaction;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import st.orm.repository.EntityRepository;
import st.orm.spring.model.Visit;
import st.orm.template.ORMTemplate;
import st.orm.template.impl.BuilderImpl;

/**
 * A transaction block binds to one data source, and the frames that share its physical transaction must agree
 * on it. A propagation that opens a physical transaction of its own, or runs without one, is a boundary: it
 * binds whichever data source it first touches, independently of the block that encloses it.
 *
 * <p>Two H2 databases with a transaction manager each stand in for two data sources. The templates share one
 * {@link SpringTransactionTemplateProvider}, as the Spring Boot starter wires them, so this exercises the
 * Spring transaction context rather than the provider-identity check.</p>
 */
class SpringMultiDataSourceTransactionTest {

    private EntityRepository<Visit, Integer> ordersVisits;
    private EntityRepository<Visit, Integer> auditVisits;

    @BeforeEach
    void setUp() {
        DataSource ordersDataSource = database("spring-multi-orders");
        DataSource auditDataSource = database("spring-multi-audit");
        List<PlatformTransactionManager> transactionManagers = List.of(
                new DataSourceTransactionManager(ordersDataSource),
                new DataSourceTransactionManager(auditDataSource));
        var transactionTemplateProvider = new SpringTransactionTemplateProvider(() -> transactionManagers);
        ordersVisits = template(ordersDataSource, transactionTemplateProvider).entity(Visit.class);
        auditVisits = template(auditDataSource, transactionTemplateProvider).entity(Visit.class);
    }

    private static ORMTemplate template(DataSource dataSource,
                                        SpringTransactionTemplateProvider transactionTemplateProvider) {
        return new BuilderImpl(st.orm.core.template.ORMTemplate.builder(dataSource)
                .connectionProvider(new SpringConnectionProvider())
                .transactionTemplateProvider(transactionTemplateProvider))
                .build();
    }

    private static DataSource database(String name) {
        DataSource dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        return dataSource;
    }

    @Test
    void oneBlockCannotSpanTwoDataSources() {
        Exception exception = assertThrows(Exception.class, () ->
                transaction(tx -> {
                    ordersVisits.count();
                    auditVisits.count();
                    return null;
                }));
        assertTrue(messageChain(exception).contains("Incompatible DataSource"), messageChain(exception));
    }

    @Test
    void requiresNewRunsAgainstAnotherDataSource() {
        transaction(tx -> {
            ordersVisits.removeAll();
            transaction(REQUIRES_NEW, audit -> {
                auditVisits.removeAll();
                return null;
            });
            tx.setRollbackOnly();
            return null;
        });
        // The audit transaction committed on its own data source; the orders transaction rolled back on its own.
        assertEquals(0, auditVisits.count());
        assertEquals(14, ordersVisits.count());
    }

    @Test
    void notSupportedRunsAgainstAnotherDataSource() {
        transaction(tx -> {
            ordersVisits.removeAll();
            transaction(NOT_SUPPORTED, report -> {
                // A read on another database while the orders transaction is open.
                assertEquals(14, auditVisits.count());
                return null;
            });
            tx.setRollbackOnly();
            return null;
        });
        assertEquals(14, ordersVisits.count());
    }

    @Test
    void joinedFrameInsideBoundaryAgreesWithTheBoundary() {
        // REQUIRED inside the audit block joins the audit transaction, not the orders one, so it must stay on
        // the audit database even though the outermost block is on the orders database.
        Exception exception = assertThrows(Exception.class, () ->
                transaction(tx -> {
                    ordersVisits.count();
                    return transaction(REQUIRES_NEW, audit -> {
                        auditVisits.count();
                        return transaction(joined -> ordersVisits.count());
                    });
                }));
        assertTrue(messageChain(exception).contains("Incompatible DataSource"), messageChain(exception));
    }

    @Test
    void nestedStaysOnTheEnclosingDataSource() {
        Exception exception = assertThrows(Exception.class, () ->
                transaction(tx -> {
                    ordersVisits.count();
                    return transaction(NESTED, nested -> auditVisits.count());
                }));
        assertTrue(messageChain(exception).contains("Incompatible DataSource"), messageChain(exception));
    }

    @Test
    void enclosingBlockBindsOnItsOwnFirstTouch() {
        transaction(tx -> {
            // The audit block touches first. It is a boundary, so it does not start the enclosing block, which
            // is free to touch the orders database afterwards.
            transaction(REQUIRES_NEW, audit -> {
                auditVisits.removeAll();
                return null;
            });
            ordersVisits.removeAll();
            tx.setRollbackOnly();
            return null;
        });
        assertEquals(0, auditVisits.count());
        assertEquals(14, ordersVisits.count());
    }

    @Test
    void neverInsideAnUntouchedBlockStillFails() {
        // The enclosing block declares a transaction whether or not it has started one yet.
        Exception exception = assertThrows(Exception.class, () ->
                transaction(tx -> transaction(NEVER, never -> auditVisits.count())));
        assertTrue(messageChain(exception).contains("Existing transaction found"), messageChain(exception));
        assertEquals(14, auditVisits.count());
    }

    private static String messageChain(Throwable throwable) {
        var builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            builder.append(current.getMessage()).append('\n');
        }
        return builder.toString();
    }
}
