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
import st.orm.Data;
import st.orm.DbColumn;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Metamodel;
import st.orm.Ref;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Version;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.nCopies;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.getColumnName;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getGenerationStrategy;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.getSequence;
import static st.orm.core.template.impl.RecordReflection.getTableName;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.RecordValidation.validateDataType;

/**
 * Factory for creating models.
 *
 * @since 1.2
 */
final class ModelFactory {
    private static final ConcurrentHashMap<Class<?>, Model<?, ?>> MODEL_CACHE = new ConcurrentHashMap<>();

    private ModelFactory () {
    }

    /**
     * Creates a new instance of the model for the given builder.
     *
     * @param builder the model builder.
     * @return a new instance of the model.
     * @param <T> the record type.
     * @param <ID> the primary key type.
     * @throws SqlTemplateException if an error occurs while creating the model.
     */
    static <T extends Data, ID> Model<T, ID> getModel(
            @Nonnull ModelBuilderImpl builder,
            @Nonnull Class<T> type,
            boolean requirePrimaryKey) throws SqlTemplateException {
        try {
            validateDataType(type, requirePrimaryKey);
            //noinspection unchecked
            return (Model<T, ID>) MODEL_CACHE.computeIfAbsent(type, ignore -> {
                try {
                    RecordType recordType = getRecordType(type);
                    AtomicInteger index = new AtomicInteger(1);
                    AtomicInteger primaryKeyIndex = new AtomicInteger(1);
                    List<Column> columns = new ArrayList<>();
                    List<RecordField> fields = new ArrayList<>();
                    RecordField pkField = null;
                    Metamodel<T, ?> rootMetamodel = Metamodel.root(type);
                    for (var field : recordType.fields()) {
                        boolean primaryKey = field.isAnnotationPresent(PK.class);
                        var list = createColumns(builder, rootMetamodel, field, primaryKey, getGenerationStrategy(field),
                                getSequence(field), false, index, primaryKeyIndex);
                        columns.addAll(list);
                        fields.addAll(nCopies(list.size(), field));
                        if (primaryKey) {
                            pkField = field;
                        }
                    }
                    var tableName = getTableName(type, builder.tableNameResolver());
                    return new ModelImpl<>(
                            recordType,
                            pkField,
                            tableName,
                            columns,
                            fields);
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SqlTemplateException ex) {
                throw ex;
            } else {
                throw e;
            }
        }
    }

    record KeyIndex(int position, int total) {}

    /**
     * Creates columns for a record component.
     *
     * @param builder the model builder.
     * @param parentMetamodel the metamodel of the parent record.
     * @param field the record field.
     * @param primaryKey whether the column is a primary key.
     * @param generation the generation strategy.
     * @param sequence the sequence name.
     * @param parentNullable whether the parent is nullable.
     * @param index the column index.
     * @return the columns for the record component.
     */
    private static List<Column> createColumns(@Nonnull ModelBuilder builder,
                                              @Nonnull Metamodel<?, ?> parentMetamodel,
                                              @Nonnull RecordField field,
                                              boolean primaryKey,
                                              @Nonnull GenerationStrategy generation,
                                              @Nonnull String sequence,
                                              boolean parentNullable,
                                              @Nonnull AtomicInteger index,
                                              @Nonnull AtomicInteger primaryKeyIndex) {
        try {
            @SuppressWarnings("unchecked")
            var metamodel = (Metamodel<Data, ?>) Metamodel.of((Class<? extends Data>) parentMetamodel.root(),
                    parentMetamodel.fieldPath().isEmpty() ? field.name() : parentMetamodel.fieldPath() + "." + field.name());
            var converter = getORMConverter(field).orElse(null);
            if (converter != null) {
                var columnTypes = converter.getParameterTypes();
                var expected = converter.getParameterCount();
                if (columnTypes.size() != expected) {
                    throw new SqlTemplateException("Expected %s parameter types, but got %d.".formatted(expected, columnTypes.size()));
                }
                var columnNames = converter.getColumns(c -> RecordReflection.getColumnName(c, builder.columnNameResolver()));
                if (columnTypes.size() != columnNames.size()) {
                    throw new SqlTemplateException("Column count does not match value count.");
                }
                var columns = new ArrayList<Column>(columnTypes.size());
                for (int i = 0; i < columnTypes.size(); i++) {
                    KeyIndex pk = primaryKey
                            ? new KeyIndex(primaryKeyIndex.getAndIncrement(), columnTypes.size())
                            : null;
                    columns.add(new ColumnImpl(columnNames.get(i), index.getAndIncrement(), columnTypes.get(i),
                            pk != null, GenerationStrategy.NONE, "",false, pk == null ? -1 : pk.position(),
                            true, true, true, false, false, metamodel));
                }
                return columns;
            }
            boolean foreignKey = field.isAnnotationPresent(FK.class);
            if (isRecord(field.type()) && !foreignKey) {
                if (!primaryKey) {
                    if (field.isAnnotationPresent(DbColumn.class)) {
                        // @Inline is implicitly assumed.
                        throw new SqlTemplateException("DbColumn annotation is not allowed for @Inline records: %s.%s.".formatted(field.type().getSimpleName(), field.name()));
                    }
                }
                return getRecordType(field.type()).fields().stream()
                        .flatMap(child -> createColumns(builder, metamodel, child, primaryKey, generation, sequence, field.nullable(), index, primaryKeyIndex).stream())
                        .toList();
            }
            boolean nullable = parentNullable || field.nullable();
            Persist persist = field.getAnnotation(Persist.class);
            boolean version = field.isAnnotationPresent(Version.class);
            // We always mark PK as insertable.
            boolean insertable = primaryKey || persist == null || persist.insertable();
            // We never mark PK as updatable.
            boolean updatable = !primaryKey && (persist == null || persist.updatable());
            boolean ref = Ref.class.isAssignableFrom(field.type());
            var dataType = field.type();
            if (ref) {
                try {
                    dataType = getRefDataType(field);
                } catch (SqlTemplateException e) {
                    throw new PersistenceException(e);
                }
            }
            if (foreignKey) {
                // In case of compound pks, all metamodels point to the foreign key object and not the underlying
                // primary key fields, similar to singular primary key fields. This means that users of the metamodel
                // that extract the field (foreign key object) must handle them appropriately.
                List<ColumnName> columnNames = getForeignKeys(field, builder.foreignKeyResolver(), builder.columnNameResolver());
                List<Column> columns = new ArrayList<>(columnNames.size());
                for (int i = 0; i < columnNames.size(); i++) {
                    var columnName = columnNames.get(i);
                    KeyIndex key = new KeyIndex(i + 1, columnNames.size());
                    //noinspection ConstantValue
                    columns.add(new ColumnImpl(columnName, index.getAndIncrement(), dataType,
                            primaryKey, generation, sequence, foreignKey, key == null ? -1 : key.position(),
                            nullable, insertable, updatable, version, ref, metamodel));
                }
                return columns;
            }
            ColumnName columnName = getColumnName(field, builder.columnNameResolver());
            KeyIndex pk = primaryKey ? new KeyIndex(1, 1) : null;
            return List.of(new ColumnImpl(columnName, index.getAndIncrement(), dataType,
                    pk != null, generation, sequence, false, pk == null ? -1 : pk.position(),
                    nullable, insertable, updatable, version, ref, metamodel));
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }
}
