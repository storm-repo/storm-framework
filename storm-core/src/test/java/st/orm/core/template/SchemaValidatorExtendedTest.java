package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.ProjectionQuery;
import st.orm.core.template.impl.SchemaValidationError;
import st.orm.core.template.impl.SchemaValidationError.ErrorKind;
import st.orm.core.template.impl.SchemaValidationException;
import st.orm.core.template.impl.SchemaValidator;

/**
 * Extended tests for {@link SchemaValidator} covering validateOrThrow (no-arg), validateAndReport,
 * formatErrors, and countTypes for non-Collection iterables.
 */
class SchemaValidatorExtendedTest {

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private DataSource dataSource;

    public record ValidCity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    @DbTable("nonexistent_table")
    public record MissingEntity(
            @PK Integer id,
            @Nonnull String name
    ) implements Entity<Integer> {}

    @ProjectionQuery("SELECT id, name FROM valid_city")
    public record ProjectionQueryType(
            @PK Integer id,
            @Nonnull String name
    ) implements st.orm.Projection<Integer> {}

    @BeforeEach
    void setUp() {
        String jdbcUrl = "jdbc:h2:mem:schema_validator_ext_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1";
        dataSource = new SimpleDataSource(jdbcUrl);
    }

    // ---- validateAndReport non-strict mode ----

    @Test
    void testValidateAndReportNonStrictExcludesWarnings() throws SQLException {
        execute("CREATE TABLE valid_city (id INTEGER AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");

        SchemaValidator validator = SchemaValidator.of(dataSource);
        // City name is nullable in DB but non-null in entity, causing NULLABILITY_MISMATCH (a warning).
        List<String> errors = validator.validateAndReport(List.of(ValidCity.class), false);
        // Non-strict mode should exclude warnings from the returned list.
        assertTrue(errors.isEmpty(), "Expected no errors in non-strict mode, got: " + errors);
    }

    @Test
    void testValidateAndReportStrictIncludesWarnings() throws SQLException {
        execute("CREATE TABLE valid_city (id INTEGER AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");

        SchemaValidator validator = SchemaValidator.of(dataSource);
        List<String> errors = validator.validateAndReport(List.of(ValidCity.class), true);
        // Strict mode should include NULLABILITY_MISMATCH as an error.
        assertFalse(errors.isEmpty(), "Expected strict errors for nullable column, got: " + errors);
    }

    // ---- validateAndReport with missing table ----

    @Test
    void testValidateAndReportWithMissingTable() {
        SchemaValidator validator = SchemaValidator.of(dataSource);
        List<String> errors = validator.validateAndReport(List.of(MissingEntity.class), true);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("nonexistent_table")));
    }

    // ---- validate with empty list returns no errors ----

    @Test
    void testValidateEmptyList() {
        SchemaValidator validator = SchemaValidator.of(dataSource);
        List<SchemaValidationError> errors = validator.validate(List.of());
        assertNotNull(errors);
        assertTrue(errors.isEmpty());
    }

    // ---- validateOrThrow with types ----

    @Test
    void testValidateOrThrowThrowsForMissingTable() {
        SchemaValidator validator = SchemaValidator.of(dataSource);
        assertThrows(SchemaValidationException.class,
                () -> validator.validateOrThrow(List.of(MissingEntity.class)));
    }

    @Test
    void testValidateOrThrowPassesForValidTable() throws SQLException {
        execute("CREATE TABLE valid_city (id INTEGER AUTO_INCREMENT, name VARCHAR(255) NOT NULL, PRIMARY KEY (id))");

        SchemaValidator validator = SchemaValidator.of(dataSource);
        // Should not throw for a valid entity.
        validator.validateOrThrow(List.of(ValidCity.class));
    }

    // ---- SchemaValidationException properties ----

    @Test
    void testSchemaValidationExceptionErrors() {
        List<SchemaValidationError> errors = List.of(
                new SchemaValidationError(ValidCity.class, ErrorKind.TABLE_NOT_FOUND, "Table not found."),
                new SchemaValidationError(ValidCity.class, ErrorKind.COLUMN_NOT_FOUND, "Column not found.")
        );
        SchemaValidationException exception = new SchemaValidationException(errors);
        assertEquals(2, exception.getErrors().size());
        assertTrue(exception.getMessage().contains("Schema validation failed"));
    }

    // ---- ProjectionQuery types are skipped ----

    @Test
    void testProjectionQueryTypeSkippedDuringValidation() {
        // ProjectionQuery types should be skipped, so no TABLE_NOT_FOUND even though no table exists.
        SchemaValidator validator = SchemaValidator.of(dataSource);
        List<SchemaValidationError> errors = validator.validate(List.of(ProjectionQueryType.class));
        assertTrue(errors.isEmpty(), "Expected ProjectionQuery to be skipped, but got: " + errors);
    }

    // ---- Helpers ----

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static final class SimpleDataSource implements DataSource {
        private final String url;

        SimpleDataSource(String url) {
            this.url = url;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() { return null; }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() { return 0; }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper.");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
