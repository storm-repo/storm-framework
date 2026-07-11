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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import st.orm.PersistenceException;
import st.orm.core.spi.ExceptionContext;
import st.orm.core.spi.ExceptionMapper;

/**
 * Exception mapper that translates SQL failures to Spring's {@link DataAccessException} hierarchy.
 *
 * <p>When the failure carries a {@link SQLException}, it is translated with Spring's
 * {@link SQLExceptionTranslator}: vendor error codes when the data source is known, falling back to
 * {@code SQLException} subclass and SQL state translation. This gives Storm repositories the same exception
 * semantics as Spring's other data access integrations: deadlocks and lock timeouts surface as
 * {@code TransientDataAccessException} subtypes (enabling retry setups), constraint violations as
 * {@code DataIntegrityViolationException} subtypes, and so on.</p>
 *
 * <p>Failures without a {@code SQLException} in their cause chain keep Storm's own semantics:
 * {@link PersistenceException} and its subtypes (such as optimistic locking and result cardinality failures)
 * pass through unchanged.</p>
 *
 * <p>The Spring Boot starters apply this mapper automatically to the template they auto-configure; disable with
 * {@code storm.exception-translation.enabled=false} or by defining your own {@code ExceptionMapper} bean.</p>
 *
 * @since 1.13
 */
public class SpringExceptionMapper implements ExceptionMapper {

    private final SQLExceptionTranslator translator;

    /**
     * Creates a mapper that translates on {@code SQLException} subclass and SQL state alone.
     */
    public SpringExceptionMapper() {
        this.translator = new SQLExceptionSubclassTranslator();
    }

    /**
     * Creates a mapper that also translates vendor error codes for the database product of the given data source.
     *
     * @param dataSource the data source used to determine the database product for error-code translation.
     */
    public SpringExceptionMapper(@Nonnull DataSource dataSource) {
        this.translator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
    }

    @Override
    public RuntimeException map(@Nonnull Throwable cause, @Nonnull ExceptionContext context) {
        SQLException sqlException = findSqlException(cause);
        if (sqlException == null) {
            return cause instanceof PersistenceException persistenceException
                    ? persistenceException
                    : new PersistenceException(cause);
        }
        String task = "Storm " + context.operation()
                + context.dataType().map(type -> " for " + type.getSimpleName()).orElse("");
        String sql = context.statement().orElse(null);
        DataAccessException translated = translator.translate(task, sql, sqlException);
        if (translated == null) {
            translated = new UncategorizedSQLException(task, sql, sqlException);
        }
        // The translated exception keeps the SQLException as its cause, but drops any wrapper between the
        // reported failure and the SQLException. The framework attaches SQL diagnostics as suppressed
        // exceptions to the reported failure, so carry the suppressed exceptions of the dropped wrappers over.
        for (Throwable wrapper = cause; wrapper != null && wrapper != sqlException; wrapper = wrapper.getCause()) {
            for (Throwable suppressed : wrapper.getSuppressed()) {
                translated.addSuppressed(suppressed);
            }
        }
        return translated;
    }

    private static @Nullable SQLException findSqlException(@Nonnull Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }
}
