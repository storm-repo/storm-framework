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

import static java.util.Objects.requireNonNull;

import io.micrometer.common.KeyValues;
import jakarta.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;

/**
 * {@link io.micrometer.observation.ObservationConvention} that reports Storm query observations with the
 * OpenTelemetry database client semantic conventions, in addition to the {@code storm.*} key values of
 * {@link StormQueryObservationConvention}.
 *
 * <p>Observability backends key their database tooling on the standard attributes: with this convention,
 * Storm queries surface in the database views of OTLP-capable backends (latency panels, service-map
 * database nodes) instead of rendering as generic custom spans. The emitted attributes:</p>
 *
 * <ul>
 *   <li>{@code db.system.name} — the database product, such as {@code mariadb} (low cardinality)</li>
 *   <li>{@code db.operation.name} — {@code SELECT}, {@code INSERT}, {@code UPDATE} or {@code DELETE}
 *   (low cardinality)</li>
 *   <li>{@code db.query.text} — the SQL statement with parameter placeholders, never parameter values
 *   (high cardinality, visible to trace handlers only)</li>
 * </ul>
 *
 * <p>The {@code storm.*} key values remain available for custom dashboards, and the {@code db.statement}
 * key value of the default convention is kept for backends that predate the stabilized conventions.</p>
 *
 * <p>Activate by handing the convention to the {@link MicrometerQueryObserver}; the Spring Boot starters
 * activate it through {@code storm.observations.semantic-conventions=otel}, and the Ktor plugin picks it
 * up from the dependency container.</p>
 *
 * @since 1.13
 */
public class OtelDatabaseObservationConvention extends StormQueryObservationConvention {

    private static final Map<String, String> JDBC_SUBPROTOCOL_TO_SYSTEM = Map.of(
            "mariadb", "mariadb",
            "mysql", "mysql",
            "postgresql", "postgresql",
            "h2", "h2database",
            "oracle", "oracle.db",
            "sqlserver", "microsoft.sql_server",
            "db2", "ibm.db2");

    /**
     * The {@code db.system.name} value for databases not covered by a well-known identifier.
     */
    public static final String OTHER_SQL = "other_sql";

    private final String dbSystemName;

    /**
     * Creates a convention reporting the given database product.
     *
     * @param dbSystemName the {@code db.system.name} value, such as {@code mariadb} or {@code postgresql};
     *                     well-known values are defined by the OpenTelemetry database conventions.
     */
    public OtelDatabaseObservationConvention(@Nonnull String dbSystemName) {
        this.dbSystemName = requireNonNull(dbSystemName, "dbSystemName");
    }

    /**
     * Creates a convention with the database product derived from a JDBC URL.
     *
     * @param jdbcUrl the JDBC URL of the data source, such as {@code jdbc:mariadb://localhost/db}.
     * @return the convention, reporting {@link #OTHER_SQL} when the URL names an unrecognized product.
     */
    public static OtelDatabaseObservationConvention fromJdbcUrl(@Nonnull String jdbcUrl) {
        String[] parts = jdbcUrl.split(":", 3);
        String subprotocol = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
        return new OtelDatabaseObservationConvention(
                JDBC_SUBPROTOCOL_TO_SYSTEM.getOrDefault(subprotocol, OTHER_SQL));
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(@Nonnull StormQueryObservationContext context) {
        return super.getLowCardinalityKeyValues(context).and(
                "db.system.name", dbSystemName,
                "db.operation.name", context.queryContext().operation().name());
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(@Nonnull StormQueryObservationContext context) {
        return context.queryContext().statement()
                .map(statement -> super.getHighCardinalityKeyValues(context).and("db.query.text", statement))
                .orElseGet(() -> super.getHighCardinalityKeyValues(context));
    }
}
