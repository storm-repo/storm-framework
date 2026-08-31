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
package st.orm.core.spi;

import java.time.Duration;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.StormConfig;

/**
 * Internal helper for reading typed values from {@link StormConfig} with safe parsing.
 *
 * <p>Invalid values are logged as warnings and fall back to the provided default, rather than throwing exceptions.
 * This prevents a configuration typo from crashing the application at startup.</p>
 */
public final class StormConfigHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("st.orm.config");

    private StormConfigHelper() {
    }

    /**
     * Returns the integer value of the property, or the default if missing or unparseable.
     */
    public static int getInt(StormConfig config, String key, int defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer value '{}' for property '{}', using default {}.", value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Returns the boolean value of the property, or the default if missing.
     */
    public static boolean getBoolean(StormConfig config, String key, boolean defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Returns the duration value of the property, or the default if missing or unparseable. Accepts a number with a
     * unit suffix ({@code 200ms}, {@code 2s}, {@code 1m}), a bare number of milliseconds, or an ISO-8601 duration
     * ({@code PT0.2S}).
     */
    public static @Nullable Duration getDuration(StormConfig config, String key, @Nullable Duration defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.trim();
        try {
            if (text.startsWith("P") || text.startsWith("-P")) {
                return Duration.parse(text);
            }
            var matcher = DURATION.matcher(text);
            if (matcher.matches()) {
                double amount = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2) == null ? "ms" : matcher.group(2);
                long nanos = switch (unit) {
                    case "ns" -> (long) amount;
                    case "us" -> (long) (amount * 1_000L);
                    case "s" -> (long) (amount * 1_000_000_000L);
                    case "m" -> (long) (amount * 60_000_000_000L);
                    case "h" -> (long) (amount * 3_600_000_000_000L);
                    default -> (long) (amount * 1_000_000L);   // Milliseconds, the unit of a bare number too.
                };
                return Duration.ofNanos(nanos);
            }
        } catch (RuntimeException ignore) {
            // Reported below.
        }
        LOGGER.warn("Invalid duration value '{}' for property '{}', using default {}.", value, key, defaultValue);
        return defaultValue;
    }

    /** A number with an optional unit: {@code 200ms}, {@code 2s}, {@code 1.5m}, {@code 200}. */
    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ns|us|ms|s|m|h)?");

    /**
     * Returns the enum value of the property, or the default if missing or unrecognized.
     */
    public static <E extends Enum<E>> E getEnum(
            StormConfig config,
            String key,
            Class<E> enumType,
            E defaultValue) {
        String value = config.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid value '{}' for property '{}', using default {}.", value, key, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Resolves the entity cache retention from the given configuration.
     *
     * @param config the configuration to read from.
     * @return the configured cache retention.
     */
    public static CacheRetention cacheRetention(StormConfig config) {
        return getEnum(config, StormConfig.ENTITY_CACHE_RETENTION, CacheRetention.class, CacheRetention.DEFAULT);
    }
}
