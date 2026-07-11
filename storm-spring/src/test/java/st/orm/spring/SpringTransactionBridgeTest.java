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
import static st.orm.TransactionPropagation.MANDATORY;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.transaction;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.IllegalTransactionStateException;
import st.orm.PersistenceException;
import st.orm.repository.EntityRepository;
import st.orm.spring.model.Pet;
import st.orm.spring.model.Visit;
import st.orm.template.ORMTemplate;
import st.orm.template.Transactions;

/**
 * Verifies that Storm's programmatic transaction API ({@link Transactions}) is bridged into Spring's
 * transaction managers when the template is composed with the Spring providers, and that Storm blocks
 * cooperate with Spring-managed transactions.
 */
class SpringTransactionBridgeTest {

    private DataSource dataSource;
    private DataSourceTransactionManager transactionManager;
    private ORMTemplate orm;
    private EntityRepository<Visit, Integer> visits;
    private Pet pet;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:bridgetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        orm = SpringOrmTemplate.of(dataSource, () -> List.of(transactionManager));
        visits = orm.entity(Visit.class);
        pet = orm.entity(Pet.class).getById(1);
    }

    private void insertVisit(String description) {
        visits.insert(new Visit(null, LocalDate.now(), description, pet, null));
    }

    @Test
    void programmaticTransactionCommitsThroughSpringManager() {
        long before = visits.count();
        transaction(tx -> {
            insertVisit("committed");
            return null;
        });
        assertEquals(before + 1, visits.count());
    }

    @Test
    void programmaticRollbackDiscardsWrites() {
        long before = visits.count();
        transaction(tx -> {
            insertVisit("discarded");
            assertEquals(before + 1, visits.count());
            tx.setRollbackOnly();
            return null;
        });
        assertEquals(before, visits.count());
    }

    @Test
    void checkedExceptionPropagatesTransparentlyAndRollsBack() {
        long before = visits.count();
        assertThrows(IOException.class, () ->
                transaction(tx -> {
                    insertVisit("failed");
                    throw new IOException("Simulated failure after the insert");
                }));
        assertEquals(before, visits.count());
    }

    @Test
    void requiresNewCommitsIndependentlyOfOuterRollback() {
        long before = visits.count();
        transaction(outer -> {
            insertVisit("outer, discarded");
            transaction(REQUIRES_NEW, inner -> {
                insertVisit("inner, committed");
                return null;
            });
            outer.setRollbackOnly();
            return null;
        });
        assertEquals(before + 1, visits.count());
    }

    @Test
    void mandatoryWithoutEnclosingTransactionThrows() {
        // MANDATORY is evaluated by Spring's transaction manager, which raises its own exception type.
        assertThrows(IllegalTransactionStateException.class, () ->
                transaction(MANDATORY, tx -> {
                    insertVisit("never");
                    return null;
                }));
    }

    @Test
    void stormBlockJoinsSpringManagedTransaction() {
        long before = visits.count();
        var springTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        springTransaction.executeWithoutResult(status -> {
            // A Storm programmatic block inside a Spring-managed transaction joins it (REQUIRED).
            transaction(tx -> {
                insertVisit("joined, discarded");
                return null;
            });
            assertEquals(before + 1, visits.count());
            status.setRollbackOnly();
        });
        // The Spring rollback discarded the write made by the joined Storm block.
        assertEquals(before, visits.count());
    }

    @Test
    void providerWithoutManagersRejectsStormInitiatedTransactions() {
        ORMTemplate unbridged = ORMTemplate.builder(dataSource)
                .connectionProvider(new SpringConnectionProvider())
                .transactionTemplateProvider(new SpringTransactionTemplateProvider())
                .build();
        EntityRepository<Visit, Integer> unbridgedVisits = unbridged.entity(Visit.class);
        PersistenceException exception = assertThrows(PersistenceException.class, () ->
                transaction(tx -> {
                    unbridgedVisits.insert(new Visit(null, LocalDate.now(), "never", pet, null));
                    return null;
                }));
        assertTrue(exception.getMessage().contains("PlatformTransactionManager supplier"));
    }
}
