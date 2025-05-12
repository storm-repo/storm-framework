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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.FK;
import st.orm.DbEnum;
import st.orm.PK;
import st.orm.Ref;
import st.orm.PersistenceException;
import st.orm.spi.ORMConverter;
import st.orm.spi.ORMReflection;
import st.orm.template.Column;
import st.orm.template.Model;
import st.orm.template.SqlTemplateException;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static st.orm.EnumType.NAME;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.spi.Providers.getORMReflection;

/**
 * Maps a record to a model.
 *
 * @param <T> the record type.
 * @param <ID> the primary key type.
 * @since 1.2
 */
@SuppressWarnings("DuplicatedCode")
final class ModelMapperImpl<T extends Record, ID> implements ModelMapper<T, ID> {
    private static final ORMReflection REFLECTION = getORMReflection();

    private final Model<T, ID> model;

    ModelMapperImpl(@Nonnull Model<T, ID> model) {
        this.model = requireNonNull(model);
    }

    /**
     * Returns {@code true} if the given primary key is the default value, {@code false} otherwise.
     *
     * @param pk the primary key to check.
     * @return {@code true} if the given primary key is the default value, {@code false} otherwise.
     */
    @Override
    public boolean isDefaultValue(@Nullable ID pk) {
        return REFLECTION.isDefaultValue(pk);
    }

    /**
     * Extracts the value for the specified column for the given record.
     *
     * @param column the column to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified column for the given record.
     * @throws SqlTemplateException if an error occurs while extracting the value.
     */
    @Override
    public Object map(@Nonnull Column column, @Nonnull T record) throws SqlTemplateException {
        var found = new AtomicReference<>();
        map(record, column::equals, (_, v) -> {
            found.setPlain(v);
            return false;
        });
        return found.getPlain();
    }

    /**
     * Maps the values from the given record to a set of columns.
     *
     * @param record the record to map.
     * @param columnFilter the filter to determine which columns to include.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    @Override
    public SequencedMap<Column, Object> map(@Nonnull T record, @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException {
        var values = new LinkedHashMap<Column, Object>();
        map(record, columnFilter, (k, v) -> {
            values.put(k, v);
            return true;
        });
        return values;
    }

    /**
     * Maps the values from the given record to a set of columns.
     *
     * @param record the record to map.
     * @param columnFilter the filter to determine which columns to include.
     * @param callback the callback to process each column and value.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    private void map(@Nonnull Record record, @Nonnull Predicate<Column> columnFilter, @Nonnull BiFunction<Column, Object, Boolean> callback) throws SqlTemplateException {
        if (record.getClass() != model.type()) {
            throw new SqlTemplateException(STR."Record type not supported: \{record.getClass().getSimpleName()}. Expected: \{model.type().getSimpleName()}.");
        }
        map(record, model.type(), false, false, new AtomicInteger(), columnFilter, callback);
    }

    /**
     * Maps the values from the given record to a set of columns.
     *
     * @param record the record to map.
     * @param recordClass the record class to map.
     * @param lookupForeignKey whether to lookup foreign keys.
     * @param parentNullable whether the parent is nullable.
     * @param index the index of the column to map.
     * @param columnFilter the filter to determine which columns to include.
     * @param callback the callback to process each column and value.
     * @return {@code true} if we will continue mapping, {@code false} otherwise.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    private boolean map(@Nullable Record record,
                        @Nonnull Class<? extends Record> recordClass,
                        boolean lookupForeignKey,
                        boolean parentNullable,
                        @Nonnull AtomicInteger index,
                        @Nonnull Predicate<Column> columnFilter,
                        @Nonnull BiFunction<Column, Object, Boolean> callback) throws SqlTemplateException {
        try {
            for (var component : recordClass.getRecordComponents()) {
                var converter = getORMConverter(component).orElse(null);
                if (converter != null) {
                    if (!processComponentWithConverter(record, component, converter, index, columnFilter, callback)) {
                        return false;
                    }
                    continue;
                }
                if (Ref.class.isAssignableFrom(component.getType())) {
                    if (!processRefComponent(record, component, parentNullable, index, columnFilter, callback)) {
                        return false;
                    }
                    continue;
                }
                if (lookupForeignKey) {
                    return processLookupForeignKey(record, recordClass, component, parentNullable, index, columnFilter, callback);
                }
                if (!processComponent(record, component, parentNullable, index, columnFilter, callback)) {
                    return false;
                }
            }
            return true;
        } catch (SqlTemplateException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }

    private boolean processComponentWithConverter(@Nullable Record record,
                                                  @Nonnull RecordComponent component,
                                                  @Nonnull ORMConverter converter,
                                                  @Nonnull AtomicInteger index,
                                                  @Nonnull Predicate<Column> columnFilter,
                                                  @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        var values = converter.getValues(record);
        int expected = converter.getParameterCount();
        if (values.size() != expected) {
            throw new SqlTemplateException(STR."Converter returned \{values.size()} values for component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}', but expected \{expected}.");
        }
        for (var value : values) {
            Column column = model.columns().get(index.getAndIncrement());
            if (columnFilter.test(column)) {
                if (!callback.apply(column, value)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean processRefComponent(@Nullable Record record,
                                        @Nonnull RecordComponent component,
                                        boolean parentNullable,
                                        @Nonnull AtomicInteger index,
                                        @Nonnull Predicate<Column> columnFilter,
                                        @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
            throw new SqlTemplateException(STR."Ref component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
        }
        var id = ofNullable(record == null
                ? null
                : (Ref<?>) REFLECTION.invokeComponent(component, record))
                .map(Ref::id)
                .orElse(null);
        if (component.getType().isRecord()) {
            //noinspection unchecked
            return map((Record) id, (Class<? extends Record>) component.getType(), true, parentNullable || !REFLECTION.isNonnull(component), index, columnFilter, callback);
        }
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (id == null && !parentNullable && REFLECTION.isNonnull(component)) {
                throw new SqlTemplateException(STR."Nonnull Ref component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
            }
            return callback.apply(column, id);
        }
        return true;
    }

    private boolean processLookupForeignKey(@Nullable Record record,
                                            @Nonnull Class<? extends Record> recordClass,
                                            @Nonnull RecordComponent component,
                                            boolean parentNullable,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        var id = record == null
                ? null
                : REFLECTION.invokeComponent(component, record);
        if (component.getType().isRecord()) {
            //noinspection unchecked
            return map((Record) id, (Class<? extends Record>) component.getType(), false, parentNullable || !REFLECTION.isNonnull(component), index, columnFilter, callback);
        }
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (id == null && !parentNullable && REFLECTION.isNonnull(component)) {
                throw new SqlTemplateException(STR."Nonnull foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
            }
            return callback.apply(column, id);
        }
        return true;
    }

    private boolean processComponent(@Nullable Record record,
                                     @Nonnull RecordComponent component,
                                     boolean parentNullable,
                                     @Nonnull AtomicInteger index,
                                     @Nonnull Predicate<Column> columnFilter,
                                     @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        boolean isPrimaryKey = REFLECTION.isAnnotationPresent(component, PK.class);
        boolean isForeignKey = REFLECTION.isAnnotationPresent(component, FK.class);
        boolean isRecord = component.getType().isRecord();
        if (isForeignKey && !isRecord) {
            throw new SqlTemplateException(STR."Foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a record.");
        }
        if (isRecord) {
            Record r = record == null
                    ? null
                    : (Record) REFLECTION.invokeComponent(component, record);
            //noinspection unchecked
            return map(r, (Class<? extends Record>) component.getType(), isForeignKey,
                    parentNullable || isPrimaryKey || !REFLECTION.isNonnull(component), index, columnFilter, callback);
        }
        Object o = record == null
                ? null
                : REFLECTION.invokeComponent(component, record);
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (o == null && !isPrimaryKey && !parentNullable && REFLECTION.isNonnull(component)) {
                throw new SqlTemplateException(STR."Nonnull component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
            }
            if (o instanceof Enum<?> e) {
                return callback.apply(column,
                        switch (ofNullable(REFLECTION.getAnnotation(component, DbEnum.class))
                                .map(DbEnum::value)
                                .orElse(NAME)) {
                            case NAME -> e.name();
                            case ORDINAL -> e.ordinal();
                        });
            }
            return callback.apply(column, o);
        }
        return true;
    }
}
