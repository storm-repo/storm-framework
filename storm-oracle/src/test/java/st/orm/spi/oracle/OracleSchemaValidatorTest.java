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
package st.orm.spi.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.impl.SchemaValidationError.ErrorKind;
import st.orm.core.template.impl.SchemaValidator;
import st.orm.tck.ContainerDataSource;
import st.orm.test.StormTest;

@Testcontainers
@StormTest(scripts = "/data.sql")
public class OracleSchemaValidatorTest {

    @SuppressWarnings("resource")
    @Container
    public static GenericContainer<?> oracleContainer = new GenericContainer<>("gvenzl/oracle-free:23")
            .withExposedPorts(1521)
            .withEnv("ORACLE_PASSWORD", "oracle")
            .withEnv("APP_USER", "test")
            .withEnv("APP_USER_PASSWORD", "test")
            .withCreateContainerCmdModifier(cmd -> {
                String dockerPlatform = System.getenv("DOCKER_PLATFORM");
                if (dockerPlatform == null || dockerPlatform.isEmpty()) {
                    dockerPlatform = "linux/arm64/v8";
                }
                cmd.withPlatform(dockerPlatform);
            })
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(1));

    public static DataSource dataSource() {
        String jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%d/FREEPDB1",
                oracleContainer.getHost(), oracleContainer.getMappedPort(1521));
        return ContainerDataSource.of(jdbcUrl, "test", "test");
    }

    private DataSource dataSource;

    @BeforeEach
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Happy path entities

    public record Vet(
            @PK Integer id,
            @Nullable String firstName,
            @Nullable String lastName
    ) implements Entity<Integer> {}

    public record Address(
            @Nullable String address,
            @Nullable String city
    ) {}

    public record Owner(
            @PK Integer id,
            @Nullable String firstName,
            @Nullable String lastName,
            Address address,
            @Nullable String telephone,
            @Nullable Integer version
    ) implements Entity<Integer> {}

    // Mismatch entities

    public record MissingTableEntity(
            @PK Integer id,
            String value
    ) implements Entity<Integer> {}

    @DbTable("vet")
    public record MissingColumnEntity(
            @PK Integer id,
            String firstName,
            String nonExistentColumn
    ) implements Entity<Integer> {}

    @DbTable("vet")
    public record TypeMismatchEntity(
            @PK Integer id,
            LocalDate firstName
    ) implements Entity<Integer> {}

    @DbTable("pet_type")
    public record NullabilityMismatchEntity(
            @PK Integer id,
            String name,
            String description
    ) implements Entity<Integer> {}

    @DbTable("vet_specialty")
    public record PrimaryKeyMismatchEntity(
            @PK(generation = NONE) Integer vetId,
            Integer specialtyId
    ) implements Entity<Integer> {}

    // Sequence entities

    @DbTable("vet")
    public record SequenceExistsEntity(
            @PK(generation = SEQUENCE, sequence = "pet_id_seq") Integer id,
            String firstName,
            String lastName
    ) implements Entity<Integer> {}

    @DbTable("vet")
    public record SequenceNotFoundEntity(
            @PK(generation = SEQUENCE, sequence = "nonexistent_seq") Integer id,
            String firstName,
            String lastName
    ) implements Entity<Integer> {}

    // Tests

    @Test
    public void testValidEntitiesPass() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(Vet.class, Owner.class));
        // Oracle uses NUMBER for all numeric types, which maps to java.sql.Types.NUMERIC (2).
        // Integer mapped to NUMERIC is a numeric narrowing (not incompatible), so only warnings are expected.
        assertFalse(errors.stream().anyMatch(error -> error.kind() != ErrorKind.TYPE_INCOMPATIBLE
                        && error.kind() != ErrorKind.TYPE_NARROWING),
                "Expected no errors other than TYPE_INCOMPATIBLE or TYPE_NARROWING but got: " + errors);
    }

    @Test
    public void testTableNotFound() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(MissingTableEntity.class));
        assertEquals(1, errors.size());
        assertEquals(ErrorKind.TABLE_NOT_FOUND, errors.getFirst().kind());
        assertTrue(errors.getFirst().message().contains("missing_table_entity"));
    }

    @Test
    public void testColumnNotFound() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(MissingColumnEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.COLUMN_NOT_FOUND
                        && error.message().contains("non_existent_column")));
    }

    @Test
    public void testTypeIncompatible() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(TypeMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.TYPE_INCOMPATIBLE
                        && error.message().contains("first_name")));
    }

    @Test
    public void testNullabilityMismatch() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(NullabilityMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.NULLABILITY_MISMATCH
                        && error.message().contains("description")));
    }

    @Test
    public void testPrimaryKeyMismatch() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(PrimaryKeyMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.PRIMARY_KEY_MISMATCH));
    }

    @Test
    public void testSequenceExists() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(SequenceExistsEntity.class));
        assertFalse(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.SEQUENCE_NOT_FOUND),
                "Expected no SEQUENCE_NOT_FOUND when pet_id_seq exists.");
    }

    @Test
    public void testSequenceNotFound() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(SequenceNotFoundEntity.class));
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.SEQUENCE_NOT_FOUND
                        && error.message().contains("nonexistent_seq")));
    }
}
