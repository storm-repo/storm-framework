/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Builds allocation-free equality checks for metamodel fields backed by {@link MethodHandle}.
 *
 * <p>This class specializes implementations based on {@code handle.type().returnType()} and keeps primitive paths
 * unboxed. Any required {@code asType(...)} adaptation is done once at construction time.</p>
 */
public final class EqualitySupport {

    private EqualitySupport() {}

    /**
     * Compiles an {@link Same} implementation for the given field getter.
     *
     * <p>The handle is expected to behave like {@code (receiverType) -> returnType}. If your handle has a different
     * shape, adapt it before calling this method.</p>
     *
     * <p>No boxing is performed for primitive return types.</p>
     */
    public static <T> Same<T> compileIsSame(@Nonnull MethodHandle handle) {
        requireNonNull(handle, "handle");
        Class<?> r = handle.type().returnType();
        if (r == int.class) {
            MethodHandle h = handle.asType(MethodType.methodType(int.class, Object.class));
            return (a, b) -> {
                int x = (int) h.invokeExact((Object) a);
                int y = (int) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == long.class) {
            MethodHandle h = handle.asType(MethodType.methodType(long.class, Object.class));
            return (a, b) -> {
                long x = (long) h.invokeExact((Object) a);
                long y = (long) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == boolean.class) {
            MethodHandle h = handle.asType(MethodType.methodType(boolean.class, Object.class));
            return (a, b) -> {
                boolean x = (boolean) h.invokeExact((Object) a);
                boolean y = (boolean) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == byte.class) {
            MethodHandle h = handle.asType(MethodType.methodType(byte.class, Object.class));
            return (a, b) -> {
                byte x = (byte) h.invokeExact((Object) a);
                byte y = (byte) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == short.class) {
            MethodHandle h = handle.asType(MethodType.methodType(short.class, Object.class));
            return (a, b) -> {
                short x = (short) h.invokeExact((Object) a);
                short y = (short) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == char.class) {
            MethodHandle h = handle.asType(MethodType.methodType(char.class, Object.class));
            return (a, b) -> {
                char x = (char) h.invokeExact((Object) a);
                char y = (char) h.invokeExact((Object) b);
                return x == y;
            };
        }
        if (r == float.class) {
            MethodHandle h = handle.asType(MethodType.methodType(float.class, Object.class));
            return (a, b) -> {
                float x = (float) h.invokeExact((Object) a);
                float y = (float) h.invokeExact((Object) b);
                return Float.floatToIntBits(x) == Float.floatToIntBits(y);
            };
        }
        if (r == double.class) {
            MethodHandle h = handle.asType(MethodType.methodType(double.class, Object.class));
            return (a, b) -> {
                double x = (double) h.invokeExact((Object) a);
                double y = (double) h.invokeExact((Object) b);
                return Double.doubleToLongBits(x) == Double.doubleToLongBits(y);
            };
        }
        MethodHandle h = handle.asType(MethodType.methodType(Object.class, Object.class));
        return (a, b) -> {
            Object x = (Object) h.invokeExact((Object) a);
            Object y = (Object) h.invokeExact((Object) b);
            return Objects.equals(x, y);
        };
    }

    /**
     * Compiles an {@link Identical} implementation for the given field getter.
     *
     * <p>Identity comparison is only defined for reference-typed fields. For primitive return types this method wraps
     * the isSame implementation.
     *
     * <p>No boxing is performed.</p>
     */
    public static <T> Identical<T> compileIsIdentical(@Nonnull MethodHandle handle) {
        requireNonNull(handle, "handle");
        Class<?> r = handle.type().returnType();
        if (r.isPrimitive()) {
            return compileIsSame(handle)::isSame;
        }
        MethodHandle h = handle.asType(MethodType.methodType(Object.class, Object.class));  // Use Object for max compatibility.
        return (a, b) -> {
            Object x = (Object) h.invokeExact((Object) a);
            Object y = (Object) h.invokeExact((Object) b);
            return x == y;
        };
    }
}