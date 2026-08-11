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
package st.orm.micrometer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.core.template.TemplateString.raw;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.spi.QueryContext;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlOperation;

/**
 * Tests for {@link MicrometerQueryObserver}: observation lifecycle, naming and key values from the default
 * convention, and end-to-end behavior on a live template.
 */
public class MicrometerQueryObserverTest {

    record TestPet(Integer id) implements Entity<Integer> {
    }

    record City(@PK Integer id, String name) implements Entity<Integer> {
    }

    private record FakeQueryContext(SqlOperation operation,
                                    Class<? extends Data> type,
                                    ExecutionKind kind,
                                    String sql,
                                    long shapeId) implements QueryContext {
        FakeQueryContext(SqlOperation operation, Class<? extends Data> type, ExecutionKind kind, String sql) {
            this(operation, type, kind, sql, 0L);
        }

        @Override
        public Optional<Class<? extends Data>> dataType() {
            return Optional.ofNullable(type);
        }

        @Override
        public OptionalInt batchSize() {
            return OptionalInt.empty();
        }

        @Override
        public Optional<String> statement() {
            return Optional.ofNullable(sql);
        }
    }

    @Test
    public void observationCarriesConventionNameAndKeyValues() {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(registry);
        var observation = observer.onExecute(new FakeQueryContext(
                SqlOperation.SELECT, TestPet.class, QueryContext.ExecutionKind.QUERY, "SELECT id FROM test_pet"));
        observation.close();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasContextualNameEqualTo("select testpet")
                .hasLowCardinalityKeyValue("storm.operation", "SELECT")
                .hasLowCardinalityKeyValue("storm.execution", "QUERY")
                .hasLowCardinalityKeyValue("storm.data_type", "TestPet")
                .hasLowCardinalityKeyValue("storm.shape", "none")
                .hasHighCardinalityKeyValue("db.statement", "SELECT id FROM test_pet")
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @Test
    public void shapeIdentityIsTaggedWhenKnown() {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(registry);
        observer.onExecute(new FakeQueryContext(
                SqlOperation.SELECT, TestPet.class, QueryContext.ExecutionKind.QUERY,
                "SELECT id FROM test_pet WHERE id IN (?, ?)", 0xCAFEL)).close();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasLowCardinalityKeyValue("storm.shape", "cafe");
    }

    @Test
    public void extraKeyValuesAreAppended() {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(registry, KeyValues.of("storm.database", "vets"));
        observer.onExecute(new FakeQueryContext(
                SqlOperation.UPDATE, null, QueryContext.ExecutionKind.UPDATE, "UPDATE vet SET x = 1")).close();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasLowCardinalityKeyValue("storm.database", "vets")
                .hasLowCardinalityKeyValue("storm.data_type", "none");
    }

    @Test
    public void errorIsRecordedOnTheObservation() {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(registry);
        var observation = observer.onExecute(new FakeQueryContext(
                SqlOperation.SELECT, null, QueryContext.ExecutionKind.QUERY, "SELECT broken"));
        var failure = new SQLException("Table not found");
        observation.error(failure);
        observation.close();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasError()
                .hasBeenStopped();
    }

    @Test
    public void observesQueriesOfALiveTemplate() throws SQLException {
        var dataSource = new SimpleDataSource("jdbc:h2:mem:micrometerObserver-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
            statement.execute("INSERT INTO city (name) VALUES ('Sun Paririe'), ('Madison')");
        }
        var registry = TestObservationRegistry.create();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(new MicrometerQueryObserver(registry))
                .build();
        var rows = orm.entity(City.class).findAll();
        assertFalse(rows.isEmpty());
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasContextualNameEqualTo("select city")
                .hasLowCardinalityKeyValue("storm.data_type", "City")
                .hasBeenStarted()
                .hasBeenStopped();
    }

    @Test
    public void statementsOfOneTemplateShareTheShapeIdentity() throws SQLException {
        var dataSource = new SimpleDataSource("jdbc:h2:mem:micrometerShape-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
            statement.execute("INSERT INTO city (name) VALUES ('Sun Prairie'), ('Madison')");
        }
        var registry = TestObservationRegistry.create();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(new MicrometerQueryObserver(registry))
                .build();
        // Different placeholder counts, one template: the shape groups them where the text would split them.
        orm.entity(City.class).findAllById(List.of(1));
        orm.entity(City.class).findAllById(List.of(1, 2));
        var shapes = new ArrayList<String>();
        TestObservationRegistryAssert.assertThat(registry)
                .hasHandledContextsThatSatisfy(handled -> handled.stream()
                        .filter(context -> "storm.query".equals(context.getName()))
                        .map(context -> context.getLowCardinalityKeyValue("storm.shape"))
                        .forEach(keyValue -> shapes.add(keyValue != null ? keyValue.getValue() : null)));
        assertEquals(2, shapes.size());
        assertNotEquals("none", shapes.get(0));
        assertEquals(shapes.get(0), shapes.get(1));
    }

    @Test
    public void failedQueryOfALiveTemplateRecordsAnError() throws SQLException {
        var dataSource = new SimpleDataSource("jdbc:h2:mem:micrometerObserverError-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE city (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255))");
        }
        var registry = TestObservationRegistry.create();
        var orm = ORMTemplate.builder(dataSource)
                .queryObserver(new MicrometerQueryObserver(registry))
                .build();
        assertThrows(PersistenceException.class, () -> orm.query(raw("SELECT * FROM does_not_exist")).getResultList());
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasError()
                .hasBeenStopped();
    }
}
