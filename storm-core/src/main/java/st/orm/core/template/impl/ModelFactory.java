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
import st.orm.DbColumn;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Metamodel;
import st.orm.Ref;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Version;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.nCopies;
import static java.util.Optional.ofNullable;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.getColumnName;
import static st.orm.core.template.impl.RecordReflection.getForeignKeys;
import static st.orm.core.template.impl.RecordReflection.getGenerationStrategy;
import static st.orm.core.template.impl.RecordReflection.getRefRecordType;
import static st.orm.core.template.impl.RecordReflection.getSequence;
import static st.orm.core.template.impl.RecordReflection.getTableName;

/**
 * Factory for creating models.
 *
 * @since 1.2
 */
final class ModelFactory {
    private static final ConcurrentHashMap<Class<?>, Model<?, ?>> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

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
    static <T extends Record, ID> Model<T, ID> getModel(
            @Nonnull ModelBuilderImpl builder,
            @Nonnull Class<T> type,
            boolean requirePrimaryKey) throws SqlTemplateException {
        try {
            //noinspection unchecked
            return (Model<T, ID>) MODEL_CACHE.computeIfAbsent(type, ignore -> {
                AtomicInteger index = new AtomicInteger(1);
                AtomicInteger primaryKeyIndex = new AtomicInteger(1);
                List<Column> columns = new ArrayList<>();
                List<Metamodel<T, ?>> metamodels = new ArrayList<>();
                RecordComponent pkComponent = null;
                List<RecordComponent> fkComponents = new ArrayList<>();
                try {
                    var components = type.getRecordComponents();
                    if (components == null) {
                        throw new SqlTemplateException("No record components found for type: %s.".formatted(type.getSimpleName()));
                    }
                    for (var component : type.getRecordComponents()) {
                        boolean primaryKey = REFLECTION.isAnnotationPresent(component, PK.class);
                        boolean foreignKey = REFLECTION.isAnnotationPresent(component, FK.class);
                        Metamodel<T, ?> model = Metamodel.of(type, component.getName());
                        var list = createColumns(builder, component, primaryKey, getGenerationStrategy(component),
                                getSequence(component), false, index, primaryKeyIndex);
                        columns.addAll(list);
                        metamodels.addAll(nCopies(list.size(), model));
                        if (primaryKey) {
                            pkComponent = component;
                        }
                        if (foreignKey) {
                            fkComponents.add(component);
                        }
                    }
                    assert columns.size() == metamodels.size();
                    var tableName = getTableName(type, builder.tableNameResolver());
                    //noinspection unchecked
                    Class<ID> pkType = (Class<ID>) REFLECTION.findPKType(type).orElse(Void.class);
                    if (requirePrimaryKey && pkType == Void.class) {
                        throw new PersistenceException("No primary key found for type: %s".formatted(type.getSimpleName()));
                    }
                    return new ModelImpl<>(tableName, type, pkType, columns, metamodels, ofNullable(pkComponent), fkComponents);
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
     * @param component the record component.
     * @param primaryKey whether the column is a primary key.
     * @param generation the generation strategy.
     * @param sequence the sequence name.
     * @param parentNullable whether the parent is nullable.
     * @param index the column index.
     * @return the columns for the record component.
     */
    private static List<Column> createColumns(@Nonnull ModelBuilder builder,
                                              @Nonnull RecordComponent component,
                                              boolean primaryKey,
                                              @Nonnull GenerationStrategy generation,
                                              @Nonnull String sequence,
                                              boolean parentNullable,
                                              @Nonnull AtomicInteger index,
                                              @Nonnull AtomicInteger primaryKeyIndex) {
        try {
            var converter = getORMConverter(component).orElse(null);
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
                            pk != null, GenerationStrategy.NONE, "",false,
                            true, true, true, false, false));
                }
                return columns;
            }
            boolean foreignKey = REFLECTION.isAnnotationPresent(component, FK.class);
            Class<?> componentType = component.getType();
            if (componentType.isRecord() && !foreignKey) {
                if (!primaryKey) {
                    if (REFLECTION.isAnnotationPresent(component, DbColumn.class)) {
                        // @Inline is implicitly assumed.
                        throw new SqlTemplateException("DbColumn annotation is not allowed for @Inline records: %s.%s.".formatted(component.getDeclaringRecord().getSimpleName(), component.getName()));
                    }
                }
                return RecordReflection.getRecordComponents(componentType).stream()
                        .flatMap(c -> createColumns(builder, c, primaryKey, generation, sequence, !REFLECTION.isNonnull(component), index, primaryKeyIndex).stream())
                        .toList();
            }
            boolean nullable = parentNullable || !REFLECTION.isNonnull(component);
            Persist persist = REFLECTION.getAnnotation(component, Persist.class);
            boolean version = REFLECTION.isAnnotationPresent(component, Version.class);
            boolean insertable = persist == null || persist.insertable();
            boolean updatable = persist == null || persist.updatable();
            boolean ref = Ref.class.isAssignableFrom(componentType);
            if (ref) {
                try {
                    componentType = getRefRecordType(component);
                } catch (SqlTemplateException e) {
                    throw new PersistenceException(e);
                }
            }
            if (foreignKey) {
                List<ColumnName> columnNames = getForeignKeys(component, builder.foreignKeyResolver(), builder.columnNameResolver());
                //noinspection unchecked
                List<RecordComponent> pkComponents = RecordReflection.getNestedPkComponents((Class<? extends Record>) componentType).toList();
                List<Column> columns = new ArrayList<>(columnNames.size());
                for (int i = 0; i < columnNames.size(); i++) {
                    var columnName = columnNames.get(i);
                    var recordComponent = pkComponents.get(i);
                    KeyIndex pk = primaryKey ? new KeyIndex(1, 1) : null;
                    KeyIndex fk = new KeyIndex(i + 1, columnNames.size());
                    //noinspection ConstantValue
                    columns.add(new ColumnImpl(columnName, index.getAndIncrement(), componentType,
                            pk != null, generation, sequence, fk != null,
                            nullable, insertable, updatable, version, ref));
                }
                return columns;
            }
            ColumnName columnName = getColumnName(component, builder.columnNameResolver());
            KeyIndex pk = primaryKey ? new KeyIndex(1, 1) : null;
            return List.of(new ColumnImpl(columnName, index.getAndIncrement(), componentType,
                    pk != null, generation, sequence, false,
                    nullable, insertable, updatable, version, ref));
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }
}
