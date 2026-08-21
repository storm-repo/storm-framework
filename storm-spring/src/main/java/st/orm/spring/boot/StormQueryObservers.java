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
package st.orm.spring.boot;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import st.orm.PersistenceException;
import st.orm.core.spi.QueryObserver;
import st.orm.micrometer.MicrometerQueryObserver;
import st.orm.micrometer.OtelDatabaseObservationConvention;
import st.orm.micrometer.StormQueryObservationContext;
import st.orm.micrometer.StormQueryObservationConvention;
import st.orm.micrometer.StormTransactionObservationContext;
import st.orm.micrometer.StormTransactionObservationConvention;

/**
 * Composes the query observer the Spring Boot integration reports Storm executions with.
 *
 * <p>The observer follows the {@code storm.observations} properties: with
 * {@code storm.observations.semantic-conventions=otel} the OpenTelemetry database semantic conventions ride
 * along, with the database product resolved from the given data source's JDBC URL, so each observer describes
 * the database it actually observes. A {@code database} name is appended to every observation as the
 * low-cardinality {@code storm.database} key value, separating the templates of a multi-database application
 * in dashboards.</p>
 *
 * <p>{@link StormObservationAutoConfiguration} builds the auto-configured template's observer through this
 * class, and the starter's {@code OrmTemplateFactory} builds one per data source it composes a template for.</p>
 *
 * @since 1.14
 */
public final class StormQueryObservers {

    private StormQueryObservers() {
    }

    /**
     * Creates a query observer reporting to the given registry.
     *
     * @param observationRegistry the registry to report observations to.
     * @param properties the Storm configuration properties bound from {@code storm.*}.
     * @param dataSource the data source the observed template runs on, used to resolve the database product for
     *                   the OpenTelemetry semantic conventions; {@code null} when no single data source applies.
     * @param database the name reported as the {@code storm.database} key value, or {@code null} to report none.
     * @param queryConvention custom query observation convention, or {@code null} for the property-driven default.
     * @param transactionConvention custom transaction observation convention, or {@code null} for the default.
     * @return the composed query observer.
     * @throws PersistenceException if {@code storm.observations.semantic-conventions} carries an unknown value.
     */
    public static QueryObserver create(
            ObservationRegistry observationRegistry,
            StormProperties properties,
            @Nullable DataSource dataSource,
            @Nullable String database,
            @Nullable ObservationConvention<StormQueryObservationContext> queryConvention,
            @Nullable ObservationConvention<StormTransactionObservationContext> transactionConvention) {
        var convention = queryConvention != null ? queryConvention : conventionFor(properties, dataSource);
        var keyValues = database == null ? KeyValues.empty() : KeyValues.of("storm.database", database);
        if (convention == null && transactionConvention == null) {
            return new MicrometerQueryObserver(observationRegistry, keyValues);
        }
        return new MicrometerQueryObserver(
                observationRegistry,
                convention != null ? convention : new StormQueryObservationConvention(),
                transactionConvention != null ? transactionConvention : new StormTransactionObservationConvention(),
                keyValues);
    }

    /**
     * Resolves the observation convention the {@code storm.observations.semantic-conventions} property names,
     * or {@code null} for Storm's own conventions. The OpenTelemetry convention resolves the database product
     * from the data source's JDBC URL, falling back to the generic SQL product when no data source is given or
     * the product cannot be determined.
     */
    private static @Nullable ObservationConvention<StormQueryObservationContext> conventionFor(
            StormProperties properties, @Nullable DataSource dataSource) {
        String semanticConventions = properties.getObservations().getSemanticConventions();
        if (semanticConventions == null || semanticConventions.isBlank()
                || "storm".equalsIgnoreCase(semanticConventions.trim())) {
            return null;
        }
        if (!"otel".equalsIgnoreCase(semanticConventions.trim())) {
            throw new PersistenceException(("Unknown storm.observations.semantic-conventions value: '%s'. "
                    + "Expected 'storm' or 'otel'.").formatted(semanticConventions));
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
