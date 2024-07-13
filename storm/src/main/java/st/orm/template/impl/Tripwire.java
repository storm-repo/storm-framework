/*
 * Copyright 2024 the original author or authors.
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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.System.getProperty;

/**
 * Inspired by JDK 8 Tripwire class.
 */
public final class Tripwire {
    private static final Logger LOGGER = Logger.getLogger(Tripwire.class.getName());

    /** System property. */
    private static final String TRIPWIRE_PROPERTY = "st.orm.tripwire";

    /** Should tripwire checks be enabled? */
    static final boolean ENABLED = Boolean.parseBoolean(getProperty(TRIPWIRE_PROPERTY, "false"));

    private Tripwire() {
        throw new AssertionError("No!");
    }

    private static ThreadLocal<Boolean> IGNORE = ThreadLocal.withInitial(() -> false);
    private static ThreadLocal<Boolean> AUTO_CLOSE = ThreadLocal.withInitial(() -> false);

    public static <T> T autoClose(@Nonnull Supplier<T> supplier) {
        if (!ENABLED) {
            return supplier.get();
        }
        AUTO_CLOSE.set(true);
        try {
            return supplier.get();
        } finally {
            AUTO_CLOSE.set(false);
        }
    }

    public static <T> T ignore(@Nonnull Supplier<T> action) {
        if (!ENABLED) {
            return action.get();
        }
        IGNORE.set(true);
        try {
            return action.get();
        } finally {
            IGNORE.set(false);
        }
    }

    public static boolean isAutoClose() {
        return AUTO_CLOSE.get();
    }

    /**
     *
     */
    static void iterator() {
        if (!ENABLED) {
            return;
        }
        if (!IGNORE.get()) {
            LOGGER.log(Level.WARNING, "Requesting iterator from resource-bounded stream. Ensure to close the stream after use, or call a terminal operation to auto-close the stream. Tripwire can be disabled by setting the system property 'st.orm.tripwire' to 'false'.", new Exception("Tripwire stack trace"));
        }
    }

    static void spliterator() {
        if (!ENABLED) {
            return;
        }
        if (!IGNORE.get()) {
            LOGGER.log(Level.WARNING, "Requesting spliterator from resource-bounded stream. Ensure to close the stream after use, or call a terminal operation to auto-close the stream. Tripwire can be disabled by setting the system property 'st.orm.tripwire' to 'false'.", new Exception("Tripwire stack trace"));
        }
    }
}
