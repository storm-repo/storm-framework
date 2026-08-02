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
package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.StormConfig;
import st.orm.core.spi.DefaultSqlDialect;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlDialect.ConstraintDiscoveryStrategy;
import st.orm.core.template.impl.SchemaValidationError.ErrorKind;

/**
 * Tests that constraint validation reports what the database actually said, and stays silent about what it could
 * not be asked.
 *
 * <p>A constraint discovery query that the database does not understand leaves the constraints unknown, which is
 * not the same as the database having none. Reporting the latter turns a dialect that does not fit the database
 * into a schema that looks broken.</p>
 */
class SchemaValidatorConstraintDiscoveryTest {

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private DataSource dataSource;

    @DbTable("parent")
    record Parent(@PK Integer id) implements Entity<Integer> {}

    @DbTable("child")
    record Child(@PK Integer id, @FK Parent parent) implements Entity<Integer> {}

    /**
     * The default dialect, reading constraints the way a database that does not match it would be read. This is what
     * a MySQL dialect does to an H2 database: {@code KEY_COLUMN_USAGE.REFERENCED_TABLE_NAME} does not exist there,
     * so the foreign key query fails.
     */
    private static final class MismatchedDialect extends DefaultSqlDialect {

        MismatchedDialect(@Nonnull StormConfig config) {
            super(config);
        }

        @Override
        public ConstraintDiscoveryStrategy constraintDiscoveryStrategy() {
            return ConstraintDiscoveryStrategy.INFORMATION_SCHEMA_REFERENCING;
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new SimpleDriverDataSource(new org.h2.Driver(),
                "jdbc:h2:mem:validator_discovery_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER NOT NULL REFERENCES parent (id))");
        }
    }

    private List<SchemaValidationError> validateWith(@Nonnull SqlDialect dialect) {
        return SchemaValidator.of(dataSource, ModelBuilder.newInstance(), dialect)
                .validate(List.of(Parent.class, Child.class));
    }

    @Test
    void foreignKeyIsFoundWithAFittingDialect() {
        List<SchemaValidationError> errors = validateWith(new DefaultSqlDialect(StormConfig.defaults()));

        assertTrue(errors.isEmpty(), "Expected no errors for a matching schema, got: " + errors);
    }

    @Test
    void foreignKeyIsNotReportedMissingWhenItsDiscoveryFailed() {
        List<SchemaValidationError> errors = validateWith(new MismatchedDialect(StormConfig.defaults()));

        // The foreign key is there; the query that would have found it failed. Claiming it is missing would send
        // the reader looking for a constraint that exists.
        assertTrue(errors.stream().noneMatch(error -> error.kind() == ErrorKind.FOREIGN_KEY_MISSING),
                "Foreign keys must not be reported as missing when they could not be read, got: " + errors);
    }

    @Test
    void primaryKeysAreStillValidatedWhenOnlyForeignKeyDiscoveryFails() {
        // The mismatched dialect reads primary and unique keys with a query H2 does understand, so those stay known
        // and keep being validated. Only the kind that failed is skipped.
        List<SchemaValidationError> errors = validateWith(new MismatchedDialect(StormConfig.defaults()));

        assertTrue(errors.stream().noneMatch(error -> error.kind() == ErrorKind.PRIMARY_KEY_MISSING),
                "Primary keys are readable here and match, got: " + errors);
        assertEquals(List.of(), errors);
    }
}
