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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import st.orm.StormConfig;

/**
 * Configuration properties for the Storm ORM framework.
 *
 * <p>These properties are bound from the {@code storm.*} namespace in {@code application.yml} or
 * {@code application.properties}. The auto-configuration builds a {@link st.orm.StormConfig} from these properties
 * and passes it to the {@code ORMTemplate} factory.</p>
 *
 * <p>Example configuration:</p>
 * <pre>{@code
 * storm:
 *   ansi-escaping: false
 *   update:
 *     default-mode: ENTITY
 *     dirty-check: INSTANCE
 *     max-shapes: 5
 * }</pre>
 *
 * @see st.orm.StormConfig
 */
@ConfigurationProperties(prefix = "storm")
public class StormProperties {

    /** Update behavior configuration. */
    private Update update = new Update();

    /** Entity cache configuration. */
    private EntityCache entityCache = new EntityCache();

    /** Template cache configuration. */
    private TemplateCache templateCache = new TemplateCache();

    /** Validation configuration. */
    private Validation validation = new Validation();

    /** Exception translation configuration. */
    private ExceptionTranslation exceptionTranslation = new ExceptionTranslation();

    /** Query observation configuration. */
    private Observations observations = new Observations();

    /** Tracing configuration. */
    private Tracing tracing = new Tracing();

    /** Per-request SQL log configuration. */
    private SqlLog sqlLog = new SqlLog();

    /** Whether to enable ANSI escape sequences in Storm's log output. */
    private Boolean ansiEscaping;

    /** Returns the update behavior configuration. */
    public Update getUpdate() { return update; }

    /** Sets the update behavior configuration. */
    public void setUpdate(Update update) { this.update = update; }

    /** Returns the entity cache configuration. */
    public EntityCache getEntityCache() { return entityCache; }

    /** Sets the entity cache configuration. */
    public void setEntityCache(EntityCache entityCache) { this.entityCache = entityCache; }

    /** Returns the template cache configuration. */
    public TemplateCache getTemplateCache() { return templateCache; }

    /** Sets the template cache configuration. */
    public void setTemplateCache(TemplateCache templateCache) { this.templateCache = templateCache; }

    /** Returns the validation configuration. */
    public Validation getValidation() { return validation; }

    /** Returns the exception translation configuration. */
    public ExceptionTranslation getExceptionTranslation() { return exceptionTranslation; }

    /** Returns the query observation configuration. */
    public Observations getObservations() { return observations; }

    /** Returns the tracing configuration. */
    public Tracing getTracing() { return tracing; }

    /** Returns the per-request SQL log configuration. */
    public SqlLog getSqlLog() { return sqlLog; }

    /** Sets the per-request SQL log configuration. */
    public void setSqlLog(SqlLog sqlLog) { this.sqlLog = sqlLog; }

    /** Sets the tracing configuration. */
    public void setTracing(Tracing tracing) { this.tracing = tracing; }

    /** Sets the query observation configuration. */
    public void setObservations(Observations observations) { this.observations = observations; }

    /** Sets the exception translation configuration. */
    public void setExceptionTranslation(ExceptionTranslation exceptionTranslation) { this.exceptionTranslation = exceptionTranslation; }

    /** Sets the validation configuration. */
    public void setValidation(Validation validation) { this.validation = validation; }

    /** Returns whether ANSI escape sequences are enabled. */
    public Boolean getAnsiEscaping() { return ansiEscaping; }

    /** Sets whether ANSI escape sequences are enabled. */
    public void setAnsiEscaping(Boolean ansiEscaping) { this.ansiEscaping = ansiEscaping; }

    /**
     * Configuration properties for Storm's update behavior.
     *
     * <p>Mapped to the {@code storm.update.*} namespace.</p>
     */
    public static class Update {

        /** The default update mode ({@code ENTITY}, {@code FIELD}, or {@code OFF}). */
        private String defaultMode;

        /** The dirty-check strategy ({@code INSTANCE} or {@code FIELD}). */
        private String dirtyCheck;

        /** The maximum number of update shapes to cache. */
        private @Nullable Integer maxShapes;

        /** Returns the default update mode. */
        public String getDefaultMode() { return defaultMode; }

        /** Sets the default update mode. */
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }

        /** Returns the dirty-check strategy. */
        public String getDirtyCheck() { return dirtyCheck; }

        /** Sets the dirty-check strategy. */
        public void setDirtyCheck(String dirtyCheck) { this.dirtyCheck = dirtyCheck; }

        /** Returns the maximum number of update shapes to cache. */
        public @Nullable Integer getMaxShapes() { return maxShapes; }

        /** Sets the maximum number of update shapes to cache. */
        public void setMaxShapes(@Nullable Integer maxShapes) { this.maxShapes = maxShapes; }
    }

    /**
     * Configuration properties for Storm's entity cache.
     *
     * <p>Mapped to the {@code storm.entity-cache.*} namespace.</p>
     */
    public static class EntityCache {

        /** The cache retention policy ({@code default} or {@code light}). */
        private String retention;

        /** Returns the cache retention policy. */
        public String getRetention() { return retention; }

        /** Sets the cache retention policy. */
        public void setRetention(String retention) { this.retention = retention; }
    }

    /**
     * Configuration properties for Storm's template cache.
     *
     * <p>Mapped to the {@code storm.template-cache.*} namespace.</p>
     */
    public static class TemplateCache {

        /** The maximum number of templates to cache. */
        private @Nullable Integer size;

        /** Returns the maximum number of templates to cache. */
        public @Nullable Integer getSize() { return size; }

        /** Sets the maximum number of templates to cache. */
        public void setSize(@Nullable Integer size) { this.size = size; }
    }

    /**
     * Configuration properties for Storm's validation behavior.
     *
     * <p>Mapped to the {@code storm.validation.*} namespace.</p>
     */
    public static class Validation {

        /**
         * Record validation mode: {@code "fail"} (default), {@code "warn"}, or {@code "none"}.
         *
         * <p>When set to {@code "fail"}, validation errors cause startup to fail with a {@code PersistenceException}.
         * When set to {@code "warn"}, errors are logged as warnings; startup continues.
         * When set to {@code "none"}, record validation is skipped entirely.</p>
         *
         * <p>Defaults to {@code "fail"} when not set. Validation runs after all singleton beans are initialized,
         * so migrations executed by beans such as Flyway or Liquibase complete first.</p>
         */
        private String recordMode;

        /**
         * Schema validation mode: {@code "none"}, {@code "warn"}, or {@code "fail"} (default).
         *
         * <p>When set to {@code "fail"}, schema validation runs at startup and blocks if mismatches are found.
         * When set to {@code "warn"}, mismatches are logged at WARN level but startup continues.
         * When set to {@code "none"}, schema validation is skipped entirely.</p>
         *
         * <p>Defaults to {@code "fail"} when not set. Validation runs after all singleton beans are initialized,
         * so migrations executed by beans such as Flyway or Liquibase complete first.</p>
         */
        private String schemaMode;

        /** Whether to treat warnings (type narrowing, nullability mismatches) as errors. */
        private Boolean strict;

        /** Returns the record validation mode. */
        public String getRecordMode() { return recordMode; }

        /** Sets the record validation mode. */
        public void setRecordMode(String recordMode) { this.recordMode = recordMode; }

        /** Returns the schema validation mode. */
        public String getSchemaMode() { return schemaMode; }

        /** Sets the schema validation mode. */
        public void setSchemaMode(String schemaMode) { this.schemaMode = schemaMode; }

        /** Returns whether strict validation is enabled. */
        public Boolean getStrict() { return strict; }

        /** Sets whether strict validation is enabled. */
        public void setStrict(Boolean strict) { this.strict = strict; }
    }

    /**
     * Maps these properties onto Storm's configuration.
     *
     * @since 1.13
     */
    /** Tracing configuration. */
    public static class Tracing {

        /**
         * Whether the current trace context is appended to SQL statements as a sqlcommenter-style comment:
         * "true" comments every statement inside a span, "sampled" comments only statements of sampled
         * traces, "false" (default) disables the comments. A per-execution comment defeats prepared
         * statement caching; prefer "sampled" when the sampling probability is below 1.0.
         */
        private String sqlComments;

        /** Returns the trace context SQL comment mode. */
        public String getSqlComments() { return sqlComments; }

        /** Sets the trace context SQL comment mode. */
        public void setSqlComments(String sqlComments) { this.sqlComments = sqlComments; }
    }

    /**
     * Per-request SQL log configuration: what one request cost the database, reported as a single summary.
     */
    public static class SqlLog {

        /** Whether each request is wrapped in a SQL log whose summary is logged. Defaults to false. */
        private boolean enabled;

        /** Number of statements to record per request; the summary counts the rest regardless. */
        private int limit = 200;

        /** Returns whether per-request scopes are enabled. */
        public boolean isEnabled() { return enabled; }

        /** Sets whether per-request scopes are enabled. */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        /** Returns the number of statements recorded per request. */
        public int getLimit() { return limit; }

        /** Sets the number of statements recorded per request. */
        public void setLimit(int limit) { this.limit = limit; }

        /**
         * Whether each execution is attributed to the application frame that caused it, shown per row as
         * {@code @ File.ext:line}. Costs a stack walk per execution while a scope records; suited to
         * development. Defaults to false.
         */
        private boolean callSites;

        /** Returns whether call sites are recorded. */
        public boolean isCallSites() { return callSites; }

        /** Sets whether call sites are recorded. */
        public void setCallSites(boolean callSites) { this.callSites = callSites; }

        /**
         * Packages whose frames are skipped when attributing an execution to a call site, so rows name the
         * code that asked for the work rather than the application's own database plumbing.
         */
        private List<String> callSiteSkip = java.util.List.of();

        /**
         * Annotations that mark a bean method as an entry point to be wrapped in its own scope, covering the
         * ways work enters the application without an HTTP request. Each invocation reports as one summary
         * named after the method ({@code ReportJob.nightly}), with the same thresholds as the per-request
         * filter.
         *
         * <p>Matching is by annotation type name, directly present on the bean method, so a default whose
         * library is absent from the classpath never matches and costs nothing. Setting the property replaces
         * the default list; an empty list turns entry-point wrapping off.</p>
         */
        private List<String> entryPoints = java.util.List.of(
                "org.springframework.scheduling.annotation.Scheduled",
                "org.springframework.scheduling.annotation.Schedules",
                "org.springframework.kafka.annotation.KafkaListener",
                "org.springframework.kafka.annotation.KafkaListeners",
                "org.springframework.kafka.annotation.KafkaHandler",
                "org.springframework.amqp.rabbit.annotation.RabbitListener",
                "org.springframework.amqp.rabbit.annotation.RabbitListeners",
                "org.springframework.amqp.rabbit.annotation.RabbitHandler",
                "org.springframework.jms.annotation.JmsListener",
                "org.springframework.jms.annotation.JmsListeners",
                "io.awspring.cloud.sqs.annotation.SqsListener");

        /** Returns the annotations that mark a bean method as an entry point. */
        public List<String> getEntryPoints() { return entryPoints; }

        /** Sets the annotations that mark a bean method as an entry point. */
        public void setEntryPoints(List<String> entryPoints) { this.entryPoints = entryPoints; }

        /** Returns the packages skipped in call-site attribution. */
        public List<String> getCallSiteSkip() { return callSiteSkip; }

        /** Sets the packages skipped in call-site attribution. */
        public void setCallSiteSkip(List<String> callSiteSkip) { this.callSiteSkip = callSiteSkip; }

        /**
         * Width a summary row aims for, such as 120 for narrow viewers or 240 for wide ones; the statement
         * text elides to what the row's other columns leave. A display property of the deployment, applied
         * once at startup.
         */
        private @Nullable Integer lineWidth;

        /** Returns the display width summary rows aim for. */
        public @Nullable Integer getLineWidth() { return lineWidth; }

        /** Sets the display width summary rows aim for. */
        public void setLineWidth(@Nullable Integer lineWidth) { this.lineWidth = lineWidth; }

        /**
         * Database time above which a single statement execution is reported under the {@code st.orm.sql.slow}
         * logger, such as {@code 200ms}: with the statement, its call site and what there is to analyze it by.
         * Independent of {@code enabled}: it needs no scope and sees every execution, on whatever thread it runs.
         * Unset means no slow log.
         */
        private @Nullable Duration slowStatement;

        /** Returns the database time above which a statement execution is reported. */
        public @Nullable Duration getSlowStatement() { return slowStatement; }

        /** Sets the database time above which a statement execution is reported. */
        public void setSlowStatement(@Nullable Duration slowStatement) { this.slowStatement = slowStatement; }

        /**
         * Slow statement lines reported per shape per minute before the rest are suppressed and counted, so a
         * degraded database names every shape that suffers without flooding the log with any of them; zero for
         * no limit. Defaults to 5.
         */
        private @Nullable Integer slowStatementLimit;

        /** Returns the slow statement lines reported per shape per minute. */
        public @Nullable Integer getSlowStatementLimit() { return slowStatementLimit; }

        /** Sets the slow statement lines reported per shape per minute. */
        public void setSlowStatementLimit(@Nullable Integer slowStatementLimit) { this.slowStatementLimit = slowStatementLimit; }

        /** Reporting thresholds; with any set, only requests that exceed one are reported. */
        private Threshold threshold = new Threshold();

        /** Returns the reporting thresholds. */
        public Threshold getThreshold() { return threshold; }

        /** Sets the reporting thresholds. */
        public void setThreshold(Threshold threshold) { this.threshold = threshold; }

        /**
         * Reporting thresholds. Without thresholds every request that touches the database is reported, which
         * suits development; with a threshold set, only requests that exceed one are reported, at WARN, which is
         * a guardrail suited to production.
         */
        public static class Threshold {

            /** Number of statements above which a request is reported. */
            private @Nullable Integer statements;

            /** Request duration above which a request is reported, such as {@code 500ms}. */
            private @Nullable Duration duration;

            /** Returns the statement threshold. */
            public @Nullable Integer getStatements() { return statements; }

            /** Sets the statement threshold. */
            public void setStatements(@Nullable Integer statements) { this.statements = statements; }

            /** Returns the duration threshold. */
            public @Nullable Duration getDuration() { return duration; }

            /** Sets the duration threshold. */
            public void setDuration(@Nullable Duration duration) { this.duration = duration; }
        }
    }

    /** Query observation configuration. */
    public static class Observations {

        /**
         * The key-value vocabulary of query observations: "storm" (default) for the storm.* key values,
         * or "otel" to add the OpenTelemetry database client semantic conventions.
         */
        private String semanticConventions;

        /** Returns the semantic conventions of query observations. */
        public String getSemanticConventions() { return semanticConventions; }

        /** Sets the semantic conventions of query observations. */
        public void setSemanticConventions(String semanticConventions) { this.semanticConventions = semanticConventions; }
    }

    /** Exception translation configuration. */
    public static class ExceptionTranslation {

        /** Whether SQL failures are translated to Spring's DataAccessException hierarchy. Defaults to true. */
        private Boolean enabled;

        /** Returns whether exception translation is enabled. */
        public Boolean getEnabled() { return enabled; }

        /** Sets whether exception translation is enabled. */
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public StormConfig toStormConfig() {
        Map<String, String> map = new HashMap<>();
        if (update.getDefaultMode() != null) {
            map.put(StormConfig.UPDATE_DEFAULT_MODE, update.getDefaultMode().trim().toUpperCase());
        }
        if (update.getDirtyCheck() != null) {
            map.put(StormConfig.UPDATE_DIRTY_CHECK, update.getDirtyCheck().trim().toUpperCase());
        }
        if (update.getMaxShapes() != null) {
            map.put(StormConfig.UPDATE_MAX_SHAPES, update.getMaxShapes().toString());
        }
        if (entityCache.getRetention() != null) {
            map.put(StormConfig.ENTITY_CACHE_RETENTION, entityCache.getRetention().trim());
        }
        if (templateCache.getSize() != null) {
            map.put(StormConfig.TEMPLATE_CACHE_SIZE, templateCache.getSize().toString());
        }
        if (ansiEscaping != null) {
            map.put(StormConfig.ANSI_ESCAPING, ansiEscaping.toString());
        }
        if (validation.getRecordMode() != null) {
            map.put(StormConfig.VALIDATION_RECORD_MODE, validation.getRecordMode().trim());
        }
        if (validation.getStrict() != null) {
            map.put(StormConfig.VALIDATION_STRICT, validation.getStrict().toString());
        }
        return StormConfig.of(map);
    }
}
