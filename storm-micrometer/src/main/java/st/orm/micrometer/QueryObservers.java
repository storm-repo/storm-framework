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

import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import st.orm.PersistenceException;
import st.orm.core.spi.QueryObserver;

/**
 * Composes the query observers the framework integrations report Storm executions with.
 *
 * <p>The Spring Boot starters and the Ktor plugin both build their observers here, so the two stacks resolve
 * the semantic conventions and the observation identity the same way: the convention follows the configured
 * semantic-conventions value, with the database product resolved from the observed data source's JDBC URL,
 * and a {@code database} name is appended to every observation as the low-cardinality {@code storm.database}
 * key value, naming the template the observation came from.</p>
 *
 * @since 1.14
 */
public final class QueryObservers {

    private QueryObservers() {
    }

    /**
     * Creates a query observer reporting to the given registry.
     *
     * @param observationRegistry the registry to report observations to.
     * @param queryConvention the query observation convention, or {@code null} for Storm's own convention.
     * @param transactionConvention the transaction observation convention, or {@code null} for the default.
     * @param database the name reported as the {@code storm.database} key value, or {@code null} to report none.
     * @return the composed query observer.
     */
    public static QueryObserver create(
            ObservationRegistry observationRegistry,
            @Nullable ObservationConvention<StormQueryObservationContext> queryConvention,
            @Nullable ObservationConvention<StormTransactionObservationContext> transactionConvention,
            @Nullable String database) {
        var keyValues = database == null ? KeyValues.empty() : KeyValues.of("storm.database", database);
        return new MicrometerQueryObserver(
                observationRegistry,
                queryConvention != null ? queryConvention : new StormQueryObservationConvention(),
                transactionConvention != null ? transactionConvention : new StormTransactionObservationConvention(),
                keyValues);
    }

    /**
     * Resolves the query observation convention a configured semantic-conventions value names, or {@code null}
     * for Storm's own conventions.
     *
     * <p>The value {@code otel} selects the OpenTelemetry database semantic conventions, with the database
     * product resolved from the data source's JDBC URL and the generic SQL product when no data source is
     * given or the product cannot be determined. The value {@code storm}, blank or absent selects Storm's own
     * conventions. Any other value fails with an error naming the configuration key, so a typo cannot silently
     * change the observation identity.</p>
     *
     * @param value the configured semantic-conventions value, or {@code null} when not configured.
     * @param propertyName the configuration key the value was read from, named in the error for unknown values.
     * @param dataSource the data source the observed template runs on, or {@code null} when none applies.
     * @return the resolved convention, or {@code null} for Storm's own conventions.
     * @throws PersistenceException if the value is neither {@code storm} nor {@code otel}.
     */
    public static @Nullable ObservationConvention<StormQueryObservationContext> semanticConventionFor(
            @Nullable String value, String propertyName, @Nullable DataSource dataSource) {
        if (value == null || value.isBlank() || "storm".equalsIgnoreCase(value.trim())) {
            return null;
        }
        if (!"otel".equalsIgnoreCase(value.trim())) {
            throw new PersistenceException(("Unknown %s value: '%s'. Expected 'storm' or 'otel'.")
                    .formatted(propertyName, value));
        }
        if (dataSource != null) {
            try (var connection = dataSource.getConnection()) {
                return OtelDatabaseObservationConvention.fromJdbcUrl(connection.getMetaData().getURL());
            } catch (Exception ignored) {
                // Fall through: the database product could not be determined.
            }
        }
        return new OtelDatabaseObservationConvention(OtelDatabaseObservationConvention.OTHER_SQL);
    }
}
