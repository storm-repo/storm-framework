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
package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Immutable, untyped configuration for the Storm ORM framework.
 *
 * <p>A {@code StormConfig} holds an immutable set of {@code String} key-value properties. Property keys use the same
 * names as the corresponding JVM system properties (e.g. {@code storm.update.default_mode}). When a requested key is
 * not present in the property map, the lookup falls back to {@link System#getProperty(String)}, so existing JVM flag
 * users are unaffected.</p>
 *
 * <h2>Usage</h2>
 *
 * <p>Programmatic configuration:</p>
 * <pre>{@code
 * StormConfig config = StormConfig.of(Map.of(
 *     "storm.update.default_mode", "FIELD",
 *     "storm.update.max_shapes", "10"
 * ));
 * ORMTemplate orm = ORMTemplate.of(dataSource, config);
 * }</pre>
 *
 * <p>When no configuration is provided, {@code ORMTemplate.of(dataSource)} uses {@link #defaults()}, which reads
 * exclusively from system properties.</p>
 *
 * @since 1.9
 */
public final class StormConfig {

    private static final StormConfig DEFAULTS = new StormConfig(Map.of());

    private final Map<String, String> properties;

    private StormConfig(@Nonnull Map<String, String> properties) {
        this.properties = Map.copyOf(properties);
    }

    /**
     * Returns the value of the property with the given key.
     *
     * <p>If the key is present in the property map, its value is returned. Otherwise, the value of the corresponding
     * JVM system property is returned. If neither is set, {@code null} is returned.</p>
     *
     * @param key the property key.
     * @return the property value, or {@code null} if not set.
     */
    @Nullable
    public String getProperty(@Nonnull String key) {
        String value = properties.get(key);
        return value != null ? value : System.getProperty(key);
    }

    /**
     * Returns the value of the property with the given key, falling back to the specified default.
     *
     * @param key the property key.
     * @param defaultValue the default value to return if the property is not set.
     * @return the property value, or {@code defaultValue} if not set.
     */
    @Nonnull
    public String getProperty(@Nonnull String key, @Nonnull String defaultValue) {
        String value = getProperty(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Creates a new {@code StormConfig} with the given properties.
     *
     * @param properties the configuration properties; must not be {@code null}.
     * @return a new immutable configuration.
     */
    @Nonnull
    public static StormConfig of(@Nonnull Map<String, String> properties) {
        return new StormConfig(properties);
    }

    /**
     * Returns a configuration that reads exclusively from JVM system properties.
     *
     * <p>This is the default configuration used when no explicit {@code StormConfig} is provided.</p>
     *
     * @return the default configuration; never {@code null}.
     */
    @Nonnull
    public static StormConfig defaults() {
        return DEFAULTS;
    }
}
