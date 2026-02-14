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

import static java.util.Collections.nCopies;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.getColumnName;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getGenerationStrategy;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.getSequence;
import static st.orm.core.template.impl.RecordReflection.getTableName;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.RecordValidation.validateDataType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import st.orm.Data;
import st.orm.DbColumn;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Ref;
import st.orm.Version;
import st.orm.core.spi.Name;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Factory for creating models.
 *
 * @since 1.2
 */
final class ModelFactory {
    private static final ConcurrentHashMap<Class<?>, Model<?, ?>> MODEL_CACHE = new ConcurrentHashMap<>();

    private ModelFactory() {
    }

    static <T extends Data, ID> Model<T, ID> getModel(@Nonnull ModelBuilderImpl builder, @Nonnull Class<T> type, boolean requirePrimaryKey) throws SqlTemplateException {
        try {
            validateDataType(type, requirePrimaryKey);
            //noinspection unchecked
            return (Model<T, ID>) MODEL_CACHE.computeIfAbsent(type, ignore -> {
                try {
                    return createModel(builder, type, requirePrimaryKey);
                } catch (SqlTemplateException e) {
                    throw new UncheckedSqlTemplateException(e);
                }
            });
        } catch (UncheckedSqlTemplateException e) {
            throw e.getCause();
        }
    }

    private static <T extends Data, ID> Model<T, ID> createModel(@Nonnull ModelBuilder builder, @Nonnull Class<T> type, boolean requirePrimaryKey) throws SqlTemplateException {
        validateDataType(type, requirePrimaryKey);
        RecordType recordType = getRecordType(type);
        List<Column> columns = new ArrayList<>();
        List<RecordField> fields = new ArrayList<>();
        try {
            BuildContext ctx = new BuildContext(builder, columns, fields, Metamodel.root(type), new AtomicInteger(1));
            for (var field : recordType.fields()) {
                createColumns(ctx, ctx.rootMetamodel(), field, false, KeyScope.none(), PkContext.none(), null, false);
            }
            var tableName = getTableName(type, builder.tableNameResolver());
            return new ModelImpl<>(recordType, tableName, fields, columns);
        } catch (UncheckedSqlTemplateException e) {
            throw e.getCause();
        }
    }

    record BuildContext(ModelBuilder builder, List<Column> columns, List<RecordField> fields, Metamodel<?, ?> rootMetamodel, AtomicInteger index) {
    }

    record ColumnSpec(boolean primaryKey,
                      boolean foreignKey,
                      boolean nullable,
                      boolean insertable,
                      boolean updatable,
                      boolean version,
                      boolean ref,
                      Class<?> dataType,
                      GenerationStrategy generation,
                      String sequence) {
    }

    record PkContext(boolean active, GenerationStrategy generation, String sequence) {
        static final PkContext NONE = new PkContext(false, GenerationStrategy.NONE, "");
        static PkContext none() {
            return NONE;
        }
        static PkContext start(@Nonnull RecordField pkField) {
            return new PkContext(true, getGenerationStrategy(pkField), getSequence(pkField));
        }
    }

    private static void createColumns(@Nonnull BuildContext ctx,
                                      @Nonnull Metamodel<?, ?> parentMetamodel,
                                      @Nonnull RecordField field,
                                      boolean parentNullable,
                                      @Nonnull KeyScope inheritedKeyScope,
                                      @Nonnull PkContext inheritedPkContext,
                                      @Nullable Metamodel<Data, ?> keyMetamodel,
                                      boolean expandingRelation) {
        try {
            boolean pkAnnotation = field.isAnnotationPresent(PK.class);
            boolean fkAnnotation = field.isAnnotationPresent(FK.class);
            if (expandingRelation && pkAnnotation) {
                if (fkAnnotation && isRecord(field.type()) && Data.class.isAssignableFrom(field.type())) {
                    // The parent's FK column already provides this entity's PK value, but we still
                    // need to expand the referenced entity's non-PK fields via a join.
                    @SuppressWarnings("unchecked")
                    Metamodel<Data, ?> ownMetamodel = (Metamodel<Data, ?>) Metamodel.of(
                            (Class<? extends Data>) parentMetamodel.root(),
                            parentMetamodel.fieldPath().isEmpty() ? field.name() : parentMetamodel.fieldPath() + "." + field.name()
                    );
                    boolean nullable = parentNullable || field.nullable();
                    expandForeignRelation(ctx, ownMetamodel, field, nullable);
                }
                return;
            }
            PkContext pkContext = pkAnnotation ? PkContext.start(field) : inheritedPkContext;
            boolean effectivePrimaryKey = pkAnnotation || pkContext.active();
            KeyScope keyScope = inheritedKeyScope;
            if (pkAnnotation || fkAnnotation) {
                keyScope = KeyScope.start(true);
            }
            @SuppressWarnings("unchecked")
            Metamodel<Data, ?> ownMetamodel = (Metamodel<Data, ?>) Metamodel.of(
                    (Class<? extends Data>) parentMetamodel.root(),
                    parentMetamodel.fieldPath().isEmpty() ? field.name() : parentMetamodel.fieldPath() + "." + field.name()
            );
            Metamodel<Data, ?> columnMetamodel = keyMetamodel != null ? keyMetamodel : ownMetamodel;
            Metamodel<Data, ?> nextKeyMetamodel = keyMetamodel;
            if (pkAnnotation && isRecord(field.type()) && !fkAnnotation) {
                nextKeyMetamodel = ownMetamodel;
                columnMetamodel = ownMetamodel;
            }
            var converter = getORMConverter(field).orElse(null);
            if (converter != null) {
                var columnTypes = converter.getParameterTypes();
                int expected = converter.getParameterCount();
                if (columnTypes.size() != expected) {
                    throw new SqlTemplateException("Expected %s parameter types, but got %d.".formatted(expected, columnTypes.size()));
                }
                var columnNames = converter.getColumns(c -> getColumnName(c, ctx.builder().columnNameResolver()));
                if (columnTypes.size() != columnNames.size()) {
                    throw new SqlTemplateException("Column count does not match value count.");
                }
                ColumnSpec spec = buildSpec(field, effectivePrimaryKey, fkAnnotation, parentNullable, pkContext);
                emitColumns(ctx, field, columnMetamodel, null, spec, keyScope, columnNames, columnTypes);
                return;
            }
            if (isRecord(field.type()) && !fkAnnotation) {
                if (!effectivePrimaryKey && field.isAnnotationPresent(DbColumn.class)) {
                    throw new SqlTemplateException("DbColumn annotation is not allowed for @Inline records: %s.%s.".formatted(field.type().getSimpleName(), field.name()));
                }
                boolean nullable = parentNullable || field.nullable();
                var nested = getRecordType(field.type());
                for (var child : nested.fields()) {
                    createColumns(ctx,
                            ownMetamodel,
                            child,
                            nullable,
                            effectivePrimaryKey ? keyScope : inheritedKeyScope,
                            pkContext,
                            nextKeyMetamodel,
                            expandingRelation);
                }
                return;
            }
            ColumnSpec spec = buildSpec(field, effectivePrimaryKey, fkAnnotation, parentNullable, pkContext);
            if (fkAnnotation) {
                var fkNames = getForeignKeys(field, ctx.builder().foreignKeyResolver(), ctx.builder().columnNameResolver());
                Metamodel<Data, ?> secondaryMetamodel = null;
                if (!spec.ref()) {
                    var pkField = findPkField(field.type()).orElseThrow(
                            () -> new SqlTemplateException("No primary key found for type: %s".formatted(field.type().getSimpleName())));
                    //noinspection unchecked
                    secondaryMetamodel = (Metamodel<Data, ?>) Metamodel.of(
                            (Class<? extends Data>) ownMetamodel.root(),
                            ownMetamodel.fieldPath().isEmpty() ? field.name() : ownMetamodel.fieldPath() + "." + pkField.name());
                }
                emitColumns(ctx, field, ownMetamodel, secondaryMetamodel, spec, keyScope, fkNames, nCopies(fkNames.size(), spec.dataType()));
                if (!spec.ref()) {
                    expandForeignRelation(ctx, ownMetamodel, field, spec.nullable());
                }
                return;
            }
            ColumnName columnName = getColumnName(field, ctx.builder().columnNameResolver());
            emitColumns(ctx, field, columnMetamodel, null, spec, keyScope, List.of(columnName), List.of(spec.dataType()));
        } catch (SqlTemplateException e) {
            throw new UncheckedSqlTemplateException(e);
        }
    }

    private static void expandForeignRelation(@Nonnull BuildContext ctx,
                                              @Nonnull Metamodel<Data, ?> foreignMetamodel,
                                              @Nonnull RecordField foreignField,
                                              boolean nullableDueToJoin) {
        Class<?> t = foreignField.type();
        if (!Data.class.isAssignableFrom(t)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Data> targetType = (Class<? extends Data>) t;
        RecordType targetRecordType = getRecordType(targetType);
        for (var child : targetRecordType.fields()) {
            createColumns(ctx, foreignMetamodel, child, nullableDueToJoin, KeyScope.none(), PkContext.none(), null, true);
        }
    }

    private static ColumnSpec buildSpec(@Nonnull RecordField field,
                                        boolean effectivePrimaryKey,
                                        boolean foreignKey,
                                        boolean parentNullable,
                                        @Nonnull PkContext pkContext) throws SqlTemplateException {
        boolean nullable = parentNullable || field.nullable();
        Persist persist = field.getAnnotation(Persist.class);
        boolean version = field.isAnnotationPresent(Version.class);
        boolean insertable = effectivePrimaryKey || persist == null || persist.insertable();
        boolean updatable = !effectivePrimaryKey && (persist == null || persist.updatable());
        boolean ref = Ref.class.isAssignableFrom(field.type());
        Class<?> dataType = field.type();
        if (ref) {
            dataType = getRefDataType(field);
        }
        GenerationStrategy generation = effectivePrimaryKey ? pkContext.generation() : GenerationStrategy.NONE;
        String sequence = effectivePrimaryKey ? pkContext.sequence() : "";
        return new ColumnSpec(effectivePrimaryKey, foreignKey, nullable, insertable, updatable, version, ref, dataType, generation, sequence);
    }

    private static void emitColumns(@Nonnull BuildContext ctx,
                                    @Nonnull RecordField field,
                                    @Nonnull Metamodel<Data, ?> metamodel,
                                    @Nullable Metamodel<Data, ?> secondaryMetamodel,
                                    @Nonnull ColumnSpec spec,
                                    @Nonnull KeyScope keyScope,
                                    @Nonnull List<? extends Name> names,
                                    @Nonnull List<Class<?>> types) throws SqlTemplateException {
        if (names.size() != types.size()) {
            throw new SqlTemplateException("Column count does not match type count.");
        }
        for (int i = 0; i < names.size(); i++) {
            int keyPos = keyScope.next();
            ctx.columns().add(new ColumnImpl(
                    names.get(i),
                    ctx.index().getAndIncrement(),
                    types.get(i),
                    spec.primaryKey(),
                    spec.generation(),
                    spec.sequence(),
                    spec.foreignKey(),
                    keyPos,
                    spec.nullable(),
                    spec.insertable(),
                    spec.updatable(),
                    spec.version(),
                    spec.ref(),
                    metamodel,
                    secondaryMetamodel
            ));
        }
        ctx.fields().addAll(nCopies(names.size(), field));
    }

    private static final class KeyScope {
        static final KeyScope NONE = new KeyScope(false, false);
        final boolean active;
        final boolean compound;
        final AtomicInteger pos;

        KeyScope(boolean active, boolean compound) {
            this.active = active;
            this.compound = compound;
            this.pos = active ? new AtomicInteger(1) : null;
        }

        static KeyScope none() {
            return NONE;
        }

        /**
         * Creates a new scope.
         *
         * @param compound whether the key is compound.
         */
        static KeyScope start(boolean compound) {
            return new KeyScope(true, compound);
        }

        int next() {
            return !active ? -1 : pos.getAndIncrement();
        }
    }
}
