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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.AbstractMetamodel;
import st.orm.Data;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Objects;

import static java.lang.invoke.MethodType.methodType;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.getRecordField;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Implementation that is used by the generated models.
 *
 * @since 1.4
 */
public final class MetamodelFactory {

    private MetamodelFactory() {
        // Prevent instantiation.
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T extends Data> Metamodel<T, ?> lookupGeneratedMetamodel(
            @Nonnull Class<T> rootTable,
            @Nonnull String path
    ) {
        try {
            Metamodel<T, ?> current = (Metamodel<T, ?>) Class.forName(rootTable.getName() + "Metamodel", true, rootTable.getClassLoader())
                    .getConstructor()
                    .newInstance();
            for (String segment : path.split("\\.")) {
                current = (Metamodel<T, ?>) readSegment(current, segment);
            }
            return current;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Creates a new metamodel for the given record type.
     */
    public static <T extends Data> Metamodel<T, T> root(@Nonnull Class<T> table) {
        Same<T> wrapped = null;
        if (Data.class.isAssignableFrom(table)) {
            var pkField = findPkField(table).orElse(null);
            if (pkField != null) {
                var pkHandle = buildGetterHandle(table, pkField.name());
                var s = EqualitySupport.compileIsSame(pkHandle);
                wrapped = s::isSame;
            }
        }
        Same<T> same = wrapped == null ? Objects::equals : wrapped;
        return new AbstractMetamodel<>(table) {
            @Override
            public T getValue(@Nonnull T record) {
                return record;
            }

            @Override
            public boolean isIdentical(@Nonnull T a, @Nullable T b) {
                return a == b;
            }

            @Override
            public boolean isSame(@Nonnull T a, @Nullable T b) {
                try {
                    return same.isSame(a, b);
                } catch (PersistenceException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new PersistenceException(e);
                }
            }
        };
    }

    /**
     * Creates a new metamodel for the given root table and path.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Data, E> Metamodel<T, E> of(@Nonnull Class<T> rootTable, @Nonnull String path) {
        Metamodel<T, ?> generated = lookupGeneratedMetamodel(rootTable, path);
        if (generated != null) {
            return (Metamodel<T, E>) generated;
        }
        if (path.isEmpty()) {
            return (Metamodel<T, E>) root(rootTable);
        }
        Class<E> fieldType;
        String effectivePath;
        StringBuilder effectiveField;
        boolean isColumn = false;
        try {
            RecordField field = getRecordField(rootTable, path);
            effectiveField = new StringBuilder(field.name());
            if (Ref.class.isAssignableFrom(field.type())) {
                fieldType = (Class<E>) getRefDataType(field);
                isColumn = true;
                effectivePath = stripLast(path);
            } else {
                fieldType = (Class<E>) field.type();
                effectivePath = stripLast(path);
                if (!isRecord(field.type()) || field.isAnnotationPresent(FK.class)) {
                    isColumn = true;
                }
            }
            // Walk up until we hit the FK boundary; everything below becomes part of field(), everything above
            // (including the FK field) becomes path().
            while (!effectivePath.isEmpty()) {
                RecordField parent = getRecordField(rootTable, effectivePath);
                if (parent.isAnnotationPresent(FK.class)) break;
                effectiveField.insert(0, parent.name() + ".");
                effectivePath = stripLast(effectivePath);
            }
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
        Metamodel<T, ? extends Data> rootModel = root(rootTable);
        String tablePath = getTablePath(rootTable, effectivePath);
        String tableField = "";
        if (!tablePath.isEmpty()
                && effectivePath.length() > tablePath.length()
                && effectivePath.startsWith(tablePath + ".")) {
            tableField = effectivePath.substring(tablePath.length() + 1);
        }
        Metamodel<T, ? extends Data> tableModel;
        if (effectivePath.isEmpty()) {
            tableModel = rootModel;
        } else {
            String tableModelPath = tablePath.isEmpty() ? "" : tablePath;
            String tableModelField = tablePath.isEmpty() ? effectivePath : tableField;
            Class<? extends Data> tableType = resolveDataTypeAtPath(rootTable, effectivePath);
            MethodHandle tableHandle = buildGetterHandle(rootTable, effectivePath);
            tableModel = new SimpleMetamodel<>(
                    rootTable,
                    tableModelPath,
                    (Class) tableType,
                    tableModelField,
                    false,
                    rootModel,
                    tableHandle
            );
        }
        String fullPath = effectivePath.isEmpty()
                ? effectiveField.toString()
                : effectivePath + "." + effectiveField;
        MethodHandle handle = buildGetterHandle(rootTable, fullPath);
        return new SimpleMetamodel<>(
                rootTable,
                effectivePath,
                fieldType,
                effectiveField.toString(),
                isColumn,
                tableModel,
                handle
        );
    }
    private static Object readSegment(@Nonnull Object instance, @Nonnull String name) throws Exception {
        Class<?> c = instance.getClass();
        // Public field (Java style, or Kotlin @JvmField).
        try {
            return c.getField(name).get(instance);
        } catch (NoSuchFieldException ignored) { }
        String cap = capitalize(name);
        // Kotlin/JavaBean getter: getX()
        try {
            Method m = c.getMethod("get" + cap);
            return m.invoke(instance);
        } catch (NoSuchMethodException ignored) { }
        // Boolean getter: isX()
        try {
            Method m = c.getMethod("is" + cap);
            return m.invoke(instance);
        } catch (NoSuchMethodException ignored) { }
        // Record-like accessor: x()
        try {
            Method m = c.getMethod(name);
            return m.invoke(instance);
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchFieldException("No metamodel member '%s' on %s.".formatted(name, c.getName()));
    }

    private static String stripLast(String p) {
        int idx = p.lastIndexOf('.');
        return idx == -1 ? "" : p.substring(0, idx);
    }

    private static String getTablePath(@Nonnull Class<? extends Data> rootTable,
                                       @Nonnull String path) {
        if (path.isEmpty()) {
            return "";
        }
        try {
            String tablePath = "";
            String candidate = "";
            String[] segments = path.split("\\.");
            for (int i = 0; i < segments.length - 1; i++) {
                candidate = candidate.isEmpty()
                        ? segments[i]
                        : candidate + "." + segments[i];
                RecordField field = getRecordField(rootTable, candidate);
                if (field.isAnnotationPresent(FK.class)) {
                    tablePath = candidate;
                }
            }
            return tablePath;
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    /**
     * Resolves the Data type at the end of {@code fullPath} (unwraps Ref<T>).
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Data> resolveDataTypeAtPath(@Nonnull Class<? extends Data> rootTable,
                                                               @Nonnull String fullPath) {
        try {
            RecordField f = getRecordField(rootTable, fullPath);
            if (Ref.class.isAssignableFrom(f.type())) {
                return getRefDataType(f);
            }
            return (Class<? extends Data>) f.type();
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }

    static String capitalize(String property) {
        if (property == null || property.isEmpty()) {
            return property;
        }
        char first = property.charAt(0);
        char upper = Character.toUpperCase(first);
        if (upper == first) {
            return property;
        }
        return upper + property.substring(1);
    }

    private static Method findAccessor(@Nonnull Class<?> type, @Nonnull String property) throws NoSuchMethodException {
        String name = capitalize(property);
        try {
            // Kotlin style: getId().
            return type.getMethod("get" + name);
        } catch (NoSuchMethodException ignored) { }
        try {
            // Boolean style: isActive().
            return type.getMethod("is" + name);
        } catch (NoSuchMethodException ignored) { }
        // Java record style: id().
        return type.getMethod(property);
    }

    /**
     * Builds a null-safe getter handle for a dotted path.
     *
     * <p>
     * Semantics:
     * - If any intermediate accessor returns null, the handle returns null.
     * - This matches metamodel getValue() semantics where parents in the hierarchy may be nullable.
     * </p>
     */
    private static MethodHandle buildGetterHandle(@Nonnull Class<?> rootType, @Nullable String fullPath) {
        try {
            if (fullPath == null || fullPath.isEmpty()) {
                return MethodHandles.identity(rootType);
            }
            MethodHandles.Lookup base = MethodHandles.lookup();
            Class<?> currentType = rootType;
            MethodHandle handle = null;
            for (String part : fullPath.split("\\.")) {
                Method m = findAccessor(currentType, part);
                MethodHandle getter = unreflect(base, currentType, m);
                if (handle == null) {
                    handle = getter;
                } else {
                    // Compose null-safe: if previous result is null -> return null, else call getter on it.
                    handle = nullSafeFilterReturnValue(handle, getter);
                }
                currentType = m.getReturnType();
            }
            return handle;
        } catch (Throwable e) {
            throw new PersistenceException(
                    new SqlTemplateException("Failed to create accessor handle for path: " + fullPath, e)
            );
        }
    }

    /**
     * Equivalent to filterReturnValue(prev, next), but returns null when prev returns null.
     *
     * <p>
     * Types:
     * - prev: (A) -> R
     * - next: (R) -> S
     * Result:
     * - (A) -> S (or null when R is null). For primitive S this cannot be null, so we only apply
     *   the null-guard when S is a reference type. For primitive S, the best we can do is call next.
     * </p>
     */
    private static MethodHandle nullSafeFilterReturnValue(@Nonnull MethodHandle prev, @Nonnull MethodHandle next) throws Throwable {
        Class<?> rType = prev.type().returnType();
        Class<?> sType = next.type().returnType();
        // If prev can never return null (primitive), normal composition is fine.
        if (rType.isPrimitive()) {
            return MethodHandles.filterReturnValue(prev, next);
        }
        // If next returns a primitive, we cannot return null from the composed handle.
        if (sType.isPrimitive()) {
            return MethodHandles.filterReturnValue(prev, next);
        }
        MethodHandles.Lookup lk = MethodHandles.lookup();
        // test: (R) -> boolean   (true when R is null)
        MethodHandle test = lk.findStatic(
                Objects.class,
                "isNull",
                methodType(boolean.class, Object.class)
        ).asType(methodType(boolean.class, rType));
        // target: (R) -> S  (returns null, ignores the argument)
        MethodHandle target = MethodHandles.dropArguments(
                MethodHandles.constant(sType, null),
                0,
                rType
        );
        // (R) -> S : if (R == null) null else next(R)
        MethodHandle guarded = MethodHandles.guardWithTest(test, target, next); // Fallback.
        // (A) -> S : feed prev(A) into guarded(...)
        return MethodHandles.filterReturnValue(prev, guarded);
    }

    private static MethodHandle unreflect(MethodHandles.Lookup base, Class<?> owner, Method m) throws Throwable {
        // Fast path for public members of public, exported types.
        try {
            return MethodHandles.publicLookup().unreflect(m);
        } catch (IllegalAccessException ignored) { }
        // Try private lookup (works when the module opens the package appropriately).
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, base);
            return lookup.unreflect(m);
        } catch (IllegalAccessException ignored) { }
        // Last resort: reflection. This may still fail if the target module/package is not opened.
        try {
            m.setAccessible(true);
            return base.unreflect(m);
        } catch (RuntimeException | IllegalAccessException e) {
            // Keep original failure information helpful.
            throw e;
        }
    }

    private static final class SimpleMetamodel<T extends Data, E>
            extends AbstractMetamodel<T, E, Object> {

        private final Class<T> root;
        private final Metamodel<T, ? extends Data> table;
        private final MethodHandle handle;
        private final Identical<T> identical;
        private final Same<T> same;

        SimpleMetamodel(@Nonnull Class<T> root,
                        @Nonnull String path,
                        @Nonnull Class<E> fieldType,
                        @Nonnull String field,
                        boolean isColumn,
                        @Nonnull Metamodel<T, ? extends Data> table,
                        @Nonnull MethodHandle handle) {
            super(fieldType, path, field, false, null, isColumn);
            this.root = root;
            this.table = table;
            this.handle = handle;
            this.identical = EqualitySupport.compileIsIdentical(handle);
            Same<T> wrapped = null;
            if (Data.class.isAssignableFrom(fieldType)) {
                var pkField = findPkField(fieldType).orElse(null);
                if (pkField != null) {
                    var pkHandle = buildGetterHandle(fieldType, pkField.name());
                    var s = EqualitySupport.compileIsSame(pkHandle);
                    wrapped = (a, b) -> {
                        Object dataA = handle.invoke(a);
                        Object dataB = handle.invoke(b);
                        if (dataA == null || dataB == null) return dataA == dataB;
                        return s.isSame(dataA, dataB);
                    };
                }
            }
            this.same = wrapped == null ? EqualitySupport.compileIsSame(handle) : wrapped;
        }

        @Override
        public Class<T> root() {
            return root;
        }

        @Override
        public Metamodel<T, ? extends Data> table() {
            return table;
        }

        @Override
        public Object getValue(@Nonnull T record) {
            try {
                return handle.invoke(record);
            } catch (PersistenceException e) {
                throw e;
            } catch (Throwable e) {
                throw new PersistenceException(e);
            }
        }

        @Override
        public boolean isIdentical(@Nonnull T a, @Nonnull T b) {
            try {
                return identical.isIdentical(a, b);
            } catch (PersistenceException e) {
                throw e;
            } catch (Throwable e) {
                throw new PersistenceException(e);
            }
        }

        @Override
        public boolean isSame(@Nonnull T a, @Nonnull T b) {
            try {
                return same.isSame(a, b);
            } catch (PersistenceException e) {
                throw e;
            } catch (Throwable e) {
                throw new PersistenceException(e);
            }
        }
    }
}