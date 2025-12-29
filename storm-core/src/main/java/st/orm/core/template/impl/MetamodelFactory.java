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
            Metamodel<T, ?> current = (Metamodel<T, ?>) Class.forName(rootTable.getName() + "Metamodel")
                    .getConstructor()
                    .newInstance();
            for (String segment : path.split("\\.")) {
                current = (Metamodel<T, ?>) current.getClass().getField(segment).get(current);
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
                handle = (handle == null) ? getter : MethodHandles.filterReturnValue(handle, getter);
                currentType = m.getReturnType();
            }
            return handle;
        } catch (Throwable e) {
            throw new PersistenceException(
                    new SqlTemplateException("Failed to create accessor handle for path: " + fullPath, e)
            );
        }
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
                        var dataA = handle.invoke(a);
                        var dataB = handle.invoke(b);
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
        public Metamodel<T, ?> table() {
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