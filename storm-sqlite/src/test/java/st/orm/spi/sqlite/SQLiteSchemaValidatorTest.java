package st.orm.spi.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;

import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.impl.SchemaValidationError.ErrorKind;
import st.orm.core.template.impl.SchemaValidator;
import st.orm.test.StormTest;

@StormTest(url = "jdbc:sqlite:target/SQLiteSchemaValidatorTest.db", scripts = "/data.sql")
public class SQLiteSchemaValidatorTest {

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

    // Tests

    @Test
    public void testValidEntitiesPass() {
        var validator = SchemaValidator.of(dataSource);
        var errors = validator.validate(List.of(Vet.class, Owner.class));
        assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
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
}
