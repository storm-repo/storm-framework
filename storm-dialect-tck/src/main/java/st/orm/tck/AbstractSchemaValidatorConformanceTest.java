package st.orm.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.GenerationStrategy.SEQUENCE;

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

/**
 * The schema mismatches every dialect is expected to report.
 *
 * <p>None of these assertions depends on the SQL a dialect generates, so unlike
 * {@link AbstractEntityRepositoryConformanceTest} this suite pins no statements. A dialect runs it by extending it and
 * annotating the subclass with {@code @StormTest}.
 *
 * <p>The entities are declared here rather than reused from {@code st.orm.tck.model} because they exist to be wrong in
 * specific ways: a table that is absent, a column that is absent, a type that cannot hold the column, a non-null
 * component over a nullable column, a key that does not match. Reusing the model would make them right.
 */
public abstract class AbstractSchemaValidatorConformanceTest {

    protected DataSource dataSource;

    @BeforeEach
    final void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Whether the dialect has sequences, and so reports {@code SEQUENCE_NOT_FOUND} at all. */
    protected boolean supportsSequences() {
        return true;
    }

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

    @Test
    public void testValidEntitiesPass() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(Vet.class, Owner.class));
        assertTrue(errors.isEmpty(), () -> "Expected no errors but got: " + errors);
    }

    @Test
    public void testTableNotFound() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(MissingTableEntity.class));
        assertEquals(1, errors.size());
        assertEquals(ErrorKind.TABLE_NOT_FOUND, errors.getFirst().kind());
        assertTrue(errors.getFirst().message().contains("missing_table_entity"));
    }

    @Test
    public void testColumnNotFound() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(MissingColumnEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.COLUMN_NOT_FOUND
                        && error.message().contains("non_existent_column")));
    }

    @Test
    public void testTypeIncompatible() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(TypeMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.TYPE_INCOMPATIBLE
                        && error.message().contains("first_name")));
    }

    @Test
    public void testNullabilityMismatch() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(NullabilityMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.NULLABILITY_MISMATCH
                        && error.message().contains("description")));
    }

    @Test
    public void testPrimaryKeyMismatch() {
        var errors = SchemaValidator.of(dataSource).validate(List.of(PrimaryKeyMismatchEntity.class));
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(error -> error.kind() == ErrorKind.PRIMARY_KEY_MISMATCH));
    }

    @Test
    public void testSequenceExists() {
        assumeTrue(supportsSequences());
        var errors = SchemaValidator.of(dataSource).validate(List.of(SequenceExistsEntity.class));
        assertFalse(errors.stream().anyMatch(error -> error.kind() == ErrorKind.SEQUENCE_NOT_FOUND),
                "Expected no SEQUENCE_NOT_FOUND when pet_id_seq exists.");
    }

    @Test
    public void testSequenceNotFound() {
        assumeTrue(supportsSequences());
        var errors = SchemaValidator.of(dataSource).validate(List.of(SequenceNotFoundEntity.class));
        assertTrue(errors.stream().anyMatch(
                error -> error.kind() == ErrorKind.SEQUENCE_NOT_FOUND
                        && error.message().contains("nonexistent_seq")));
    }
}
