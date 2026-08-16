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
package st.orm.spring.boot.test;

import static org.assertj.core.api.Assertions.assertThat;
import static st.orm.test.TestDatabase.POSTGRESQL;

import javax.sql.DataSource;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import st.orm.spring.boot.test.domain.VisitRepository;

/**
 * Verifies the {@code database} attribute of the slice: the context runs on a database provisioned inside the
 * shared PostgreSQL container, {@code schema.sql} and {@code data.sql} initialize it as they do the embedded
 * database, and each test still runs in a rollback transaction.
 */
@TestMethodOrder(OrderAnnotation.class)
@DataStormTest(database = POSTGRESQL)
class DataStormTestPostgreSqlTest {

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    @Order(1)
    void dataSourcePointsAtTheContainerDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).startsWith("storm_");
        }
        assertThat(environment.getProperty("spring.test.database.replace")).isEqualTo("none");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("always");
    }

    @Test
    @Order(2)
    void scriptsInitializeTheContainerDatabase() {
        assertThat(visitRepository.count()).isEqualTo(3);
    }

    @Test
    @Order(4)
    void previousTestsWritesWereRolledBack() {
        assertThat(visitRepository.count()).isEqualTo(3);
    }
}
