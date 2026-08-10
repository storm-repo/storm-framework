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

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.TransactionPropagation.REQUIRES_NEW;
import static st.orm.template.Transactions.transaction;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import st.orm.repository.EntityRepository;
import st.orm.spring.model.Pet;
import st.orm.spring.model.Visit;
import st.orm.template.ORMTemplate;
import st.orm.template.Transactions;

/**
 * Verifies that Storm's programmatic transaction API ({@link Transactions}) is bridged into a
 * {@link JpaTransactionManager}, which is the transaction manager a Spring Boot application gets when JPA is
 * on the class path. The manager is matched by the {@code DataSource} backing its entity manager factory, so
 * Storm-initiated transactions and JPA share one transaction system without a
 * {@code DataSourceTransactionManager} being present.
 */
class SpringJpaTransactionBridgeTest {

    private DataSource dataSource;
    private LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;
    private JpaTransactionManager transactionManager;
    private ORMTemplate orm;
    private EntityRepository<Visit, Integer> visits;
    private Pet pet;

    @BeforeEach
    void setUp() {
        dataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:jpabridgetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setDataSource(dataSource);
        entityManagerFactoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactoryBean.setPersistenceUnitName("jpa-bridge-test");
        // The persistence unit carries no JPA entities; it exists to back the transaction manager.
        entityManagerFactoryBean.setPackagesToScan(getClass().getPackageName());
        entityManagerFactoryBean.afterPropertiesSet();
        EntityManagerFactory entityManagerFactory = requireNonNull(entityManagerFactoryBean.getObject());
        transactionManager = new JpaTransactionManager(entityManagerFactory);
        orm = SpringOrmTemplate.of(dataSource, () -> List.of(transactionManager));
        visits = orm.entity(Visit.class);
        pet = orm.entity(Pet.class).getById(1);
    }

    @AfterEach
    void tearDown() {
        entityManagerFactoryBean.destroy();
    }

    private void insertVisit(String description) {
        visits.insert(new Visit(null, LocalDate.now(), description, pet, Instant.now()));
    }

    @Test
    void programmaticTransactionCommitsThroughJpaManager() {
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
    void stormBlockJoinsJpaManagedTransaction() {
        long before = visits.count();
        var springTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        springTransaction.executeWithoutResult(status -> {
            // A Storm programmatic block inside a JPA-managed transaction joins it (REQUIRED).
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
    void jpaManagerForAnotherDataSourceIsNotMatched() {
        DataSource otherDataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:jpabridgeother;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        // Resolution inspects the manager's DataSource only, so no entity manager factory is needed.
        var otherManager = new JpaTransactionManager();
        otherManager.setDataSource(otherDataSource);
        ORMTemplate bridged = SpringOrmTemplate.of(dataSource,
                () -> List.of(otherManager, new DataSourceTransactionManager(dataSource)));
        EntityRepository<Visit, Integer> bridgedVisits = bridged.entity(Visit.class);
        long before = bridgedVisits.count();
        transaction(tx -> {
            bridgedVisits.insert(new Visit(null, LocalDate.now(), "committed", pet, Instant.now()));
            return null;
        });
        assertEquals(before + 1, bridgedVisits.count());
    }

    @Test
    void multipleManagersForTheSameDataSourceFailFast() {
        ORMTemplate ambiguous = SpringOrmTemplate.of(dataSource,
                () -> List.of(new DataSourceTransactionManager(dataSource), transactionManager));
        EntityRepository<Visit, Integer> ambiguousVisits = ambiguous.entity(Visit.class);
        long before = ambiguousVisits.count();
        Exception exception = assertThrows(Exception.class, () ->
                transaction(tx -> {
                    ambiguousVisits.insert(new Visit(null, LocalDate.now(), "never", pet, Instant.now()));
                    return null;
                }));
        String message = messageChain(exception);
        assertTrue(message.contains("Multiple TransactionManagers found"), message);
        assertTrue(message.contains(DataSourceTransactionManager.class.getName()), message);
        assertTrue(message.contains(JpaTransactionManager.class.getName()), message);
        assertEquals(before, ambiguousVisits.count());
    }

    private static String messageChain(Throwable throwable) {
        var builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            builder.append(current.getMessage()).append('\n');
        }
        return builder.toString();
    }
}
