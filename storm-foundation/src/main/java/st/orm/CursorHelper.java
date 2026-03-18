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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bridge to cursor serialization implementation in storm-core. Mirrors the {@link MetamodelHelper} pattern:
 * foundation defines the public API ({@link Scrollable#toCursor()}, {@link Scrollable#fromCursor}), and this
 * helper delegates to {@code st.orm.core.cursor.CursorFactory} via reflection.
 */
class CursorHelper {

    private static final Method TO_CURSOR_METHOD;
    private static final Method FROM_CURSOR_METHOD;

    static {
        try {
            Class<?> factoryClass = Class.forName("st.orm.core.spi.CursorFactory");
            TO_CURSOR_METHOD = factoryClass.getMethod(
                    "toCursor", int.class, boolean.class, int.class, Object.class, Object.class);
            FROM_CURSOR_METHOD = factoryClass.getMethod(
                    "fromCursor", int.class, String.class, Class.class, Class.class);
        } catch (ReflectiveOperationException e) {
            var ex = new ExceptionInInitializerError(
                    "Failed to initialize cursor serialization. "
                            + "Please ensure that storm-core is present in the classpath.");
            ex.initCause(e);
            throw ex;
        }
    }

    private CursorHelper() {}

    /**
     * Serializes cursor values into a Base64 URL-safe string.
     */
    static String toCursor(int metamodelFingerprint, boolean isForward, int size,
                            @Nullable Object keyCursor, @Nullable Object sortCursor) {
        try {
            try {
                return (String) TO_CURSOR_METHOD.invoke(null, metamodelFingerprint, isForward, size,
                        keyCursor, sortCursor);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new PersistenceException("Reflection invocation failed for CursorFactory.toCursor.", e);
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    /**
     * Deserializes a cursor string. Returns {isForward, size, keyCursor, sortCursor}.
     */
    static Object[] fromCursor(int metamodelFingerprint, @Nonnull String cursor,
                                @Nullable Class<?> keyFieldType, @Nullable Class<?> sortFieldType) {
        try {
            try {
                return (Object[]) FROM_CURSOR_METHOD.invoke(null, metamodelFingerprint, cursor,
                        keyFieldType, sortFieldType);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new PersistenceException("Reflection invocation failed for CursorFactory.fromCursor.", e);
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}
