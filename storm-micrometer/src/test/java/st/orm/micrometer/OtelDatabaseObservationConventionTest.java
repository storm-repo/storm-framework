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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Entity;
import st.orm.spi.QueryContext;
import st.orm.spi.SqlOperation;

/**
 * Tests for {@link OtelDatabaseObservationConvention}: the OpenTelemetry database attributes are emitted
 * alongside the {@code storm.*} key values of the default convention.
 */
public class OtelDatabaseObservationConventionTest {

    record TestPet(Integer id) implements Entity<Integer> {
    }

    private record FakeQueryContext(SqlOperation operation,
                                    Class<? extends Data> type,
                                    ExecutionKind kind,
                                    String sql) implements QueryContext {
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
    public void emitsOtelDatabaseAttributesAlongsideStormKeyValues() {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(
                registry, new OtelDatabaseObservationConvention("mariadb"), KeyValues.empty());
        observer.onExecute(new FakeQueryContext(
                SqlOperation.SELECT, TestPet.class, QueryContext.ExecutionKind.QUERY, "SELECT id FROM test_pet"))
                .close();
        TestObservationRegistryAssert.assertThat(registry)
                .hasObservationWithNameEqualTo("storm.query")
                .that()
                .hasLowCardinalityKeyValue("db.system.name", "mariadb")
                .hasLowCardinalityKeyValue("db.operation.name", "SELECT")
                .hasLowCardinalityKeyValue("storm.operation", "SELECT")
                .hasLowCardinalityKeyValue("storm.data_type", "TestPet")
                .hasHighCardinalityKeyValue("db.query.text", "SELECT id FROM test_pet")
                .hasHighCardinalityKeyValue("db.statement", "SELECT id FROM test_pet");
    }

    @Test
    public void fromJdbcUrlMapsWellKnownProducts() {
        assertEquals("mariadb", systemOf("jdbc:mariadb://localhost:3306/db"));
        assertEquals("postgresql", systemOf("jdbc:postgresql://localhost/db"));
        assertEquals("h2database", systemOf("jdbc:h2:mem:test"));
        assertEquals("microsoft.sql_server", systemOf("jdbc:sqlserver://localhost"));
        assertEquals("oracle.db", systemOf("jdbc:oracle:thin:@localhost"));
        assertEquals(OtelDatabaseObservationConvention.OTHER_SQL, systemOf("jdbc:exotic://localhost"));
    }

    private static String systemOf(String url) {
        var registry = TestObservationRegistry.create();
        var observer = new MicrometerQueryObserver(
                registry, OtelDatabaseObservationConvention.fromJdbcUrl(url), KeyValues.empty());
        observer.onExecute(new FakeQueryContext(
                SqlOperation.SELECT, null, QueryContext.ExecutionKind.QUERY, null)).close();
        var system = new String[1];
        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat()
                .satisfies(context -> system[0] = context.getLowCardinalityKeyValue("db.system.name").getValue());
        return system[0];
    }
}
