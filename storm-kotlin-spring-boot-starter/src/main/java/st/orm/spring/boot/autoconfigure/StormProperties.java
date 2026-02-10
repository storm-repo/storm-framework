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
package st.orm.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        private Integer maxShapes;

        /** Returns the default update mode. */
        public String getDefaultMode() { return defaultMode; }

        /** Sets the default update mode. */
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }

        /** Returns the dirty-check strategy. */
        public String getDirtyCheck() { return dirtyCheck; }

        /** Sets the dirty-check strategy. */
        public void setDirtyCheck(String dirtyCheck) { this.dirtyCheck = dirtyCheck; }

        /** Returns the maximum number of update shapes to cache. */
        public Integer getMaxShapes() { return maxShapes; }

        /** Sets the maximum number of update shapes to cache. */
        public void setMaxShapes(Integer maxShapes) { this.maxShapes = maxShapes; }
    }

    /**
     * Configuration properties for Storm's entity cache.
     *
     * <p>Mapped to the {@code storm.entity-cache.*} namespace.</p>
     */
    public static class EntityCache {

        /** The cache retention policy ({@code minimal} or {@code aggressive}). */
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
        private Integer size;

        /** Returns the maximum number of templates to cache. */
        public Integer getSize() { return size; }

        /** Sets the maximum number of templates to cache. */
        public void setSize(Integer size) { this.size = size; }
    }

    /**
     * Configuration properties for Storm's validation behavior.
     *
     * <p>Mapped to the {@code storm.validation.*} namespace.</p>
     */
    public static class Validation {

        /** Whether to skip validation entirely. */
        private Boolean skip;

        /** Whether to treat validation errors as warnings instead of failures. */
        private Boolean warningsOnly;

        /** Returns whether validation is skipped. */
        public Boolean getSkip() { return skip; }

        /** Sets whether validation is skipped. */
        public void setSkip(Boolean skip) { this.skip = skip; }

        /** Returns whether validation errors are treated as warnings. */
        public Boolean getWarningsOnly() { return warningsOnly; }

        /** Sets whether validation errors are treated as warnings. */
        public void setWarningsOnly(Boolean warningsOnly) { this.warningsOnly = warningsOnly; }
    }
}
