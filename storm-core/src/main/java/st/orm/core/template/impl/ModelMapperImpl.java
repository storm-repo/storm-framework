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
import st.orm.Data;
import st.orm.FK;
import st.orm.DbEnum;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.Ref;
import st.orm.PersistenceException;
import st.orm.core.spi.ORMConverter;
import st.orm.core.spi.ORMReflection;
import st.orm.mapping.RecordField;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static st.orm.EnumType.NAME;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.spi.Providers.getORMReflection;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Maps a record to a model.
 *
 * @param <T> the record type.
 * @param <ID> the primary key type.
 * @since 1.2
 */
final class ModelMapperImpl<T extends Data, ID> implements ModelMapper<T, ID> {
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
        map(record, column::equals, (ignore, v) -> {
            found.setPlain(v);
            return false;
        });
        return found.getPlain();
    }

    /**
     * Maps the values from the given record to a set of columns. The result is limited to the fields specified, and
     * the columns allowed by the given filter.
     *
     * @param record the record to map.
     * @param fields the fields to map, or an empty list to map all fields.
     * @param columnFilter the filter to determine which columns to include.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     * @since 1.7
     */
    @Override
    public SequencedMap<Column, Object> map(@Nonnull T record,
                                                     @Nonnull Collection<Metamodel<? extends T, ?>> fields,
                                                     @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException {
        if (fields.isEmpty()) {
            return map(record, columnFilter);
        }
        var fieldSet = Set.copyOf(fields);
        BitSet bitSet = new BitSet(model.columns().size());
        for (Column column : model.columns()) {
            if (fieldSet.contains(column.metamodel())) {
                bitSet.set(column.index() - 1);
            }
        }
        return map(record, column -> bitSet.get(column.index() - 1));
    }

    /**
     * Maps the values from the given record to a set of columns. The result is limited to the columns allowed by the
     * given filter.
     *
     * @param record the record to map.
     * @param columnFilter the filter to determine which columns to include.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    @Override
    public SequencedMap<Column, Object> map(@Nonnull T record,
                                                     @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException {
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
    private void map(@Nonnull Data record, @Nonnull Predicate<Column> columnFilter, @Nonnull BiFunction<Column, Object, Boolean> callback) throws SqlTemplateException {
        if (record.getClass() != model.type()) {
            throw new SqlTemplateException("Record type not supported: %s. Expected: %s.".formatted(record.getClass().getSimpleName(), model.type().getSimpleName()));
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
    private boolean map(@Nullable Object record,
                        @Nonnull Class<?> recordClass,
                        boolean lookupForeignKey,
                        boolean parentNullable,
                        @Nonnull AtomicInteger index,
                        @Nonnull Predicate<Column> columnFilter,
                        @Nonnull BiFunction<Column, Object, Boolean> callback) throws SqlTemplateException {
        try {
            var type = getRecordType(recordClass);
            for (var field : type.fields()) {
                var converter = getORMConverter(field).orElse(null);
                if (converter != null) {
                    if (!processComponentWithConverter(record, field, converter, index, columnFilter, callback)) {
                        return false;
                    }
                    continue;
                }
                if (Ref.class.isAssignableFrom(field.type())) {
                    if (!processRefComponent(record, field, parentNullable, index, columnFilter, callback)) {
                        return false;
                    }
                    continue;
                }
                if (lookupForeignKey) {
                    return processLookupForeignKey(record, field, parentNullable, index, columnFilter, callback);
                }
                if (!processField(record, field, parentNullable, index, columnFilter, callback)) {
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

    private boolean processComponentWithConverter(@Nullable Object record,
                                                  @Nonnull RecordField field,
                                                  @Nonnull ORMConverter converter,
                                                  @Nonnull AtomicInteger index,
                                                  @Nonnull Predicate<Column> columnFilter,
                                                  @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        var values = converter.toDatabase(record);
        int expected = converter.getParameterCount();
        if (values.size() != expected) {
            throw new SqlTemplateException("Converter returned %d values for component '%s.%s', but expected %d."
                    .formatted(values.size(), field.type().getSimpleName(), field.name(), expected));
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

    private boolean processRefComponent(@Nullable Object record,
                                        @Nonnull RecordField field,
                                        boolean parentNullable,
                                        @Nonnull AtomicInteger index,
                                        @Nonnull Predicate<Column> columnFilter,
                                        @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (!field.isAnnotationPresent(FK.class)) {
            throw new SqlTemplateException("Ref component '%s.%s' is not a foreign key.".formatted(field.type().getSimpleName(), field.name()));
        }
        var id = ofNullable(record == null
                ? null
                : (Ref<?>) REFLECTION.invoke(field, record))
                .map(Ref::id)
                .orElse(null);
        if (isRecord(field.type())) {
            return map(id, field.type(), true, parentNullable || field.nullable(), index, columnFilter, callback);
        }
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (id == null && !parentNullable && !field.nullable()) {
                throw new SqlTemplateException("Non-null Ref component '%s.%s' is null.".formatted(field.type().getSimpleName(), field.name()));
            }
            return callback.apply(column, id);
        }
        return true;
    }

    private boolean processLookupForeignKey(@Nullable Object record,
                                            @Nonnull RecordField field,
                                            boolean parentNullable,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        var id = record == null
                ? null
                : REFLECTION.invoke(field, record);
        if (isRecord(field.type())) {
            boolean isForeignKey = field.isAnnotationPresent(FK.class); // Primary key can be a foreign key as well.
            return map(id, field.type(), isForeignKey, parentNullable || field.nullable(), index, columnFilter, callback);
        }
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (id == null && !parentNullable && !field.nullable()) {
                throw new SqlTemplateException("Non-null foreign key component '%s.%s' is null.".formatted(field.type().getSimpleName(), field.name()));
            }
            return callback.apply(column, id);
        }
        return true;
    }

    private boolean processField(@Nullable Object record,
                                 @Nonnull RecordField field,
                                 boolean parentNullable,
                                 @Nonnull AtomicInteger index,
                                 @Nonnull Predicate<Column> columnFilter,
                                 @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        boolean isPrimaryKey = field.isAnnotationPresent(PK.class);
        boolean isForeignKey = field.isAnnotationPresent(FK.class);
        boolean isRecord = isRecord(field.type());
        if (isForeignKey && !isRecord) {
            throw new SqlTemplateException("Foreign key component '%s.%s' is not a record.".formatted(field.type().getSimpleName(), field.name()));
        }
        if (isRecord) {
            Object r = record == null
                    ? null
                    : REFLECTION.invoke(field, record);
            return map(r, field.type(), isForeignKey,
                    parentNullable || isPrimaryKey || field.nullable(), index, columnFilter, callback);
        }
        Object o = record == null
                ? null
                : REFLECTION.invoke(field, record);
        Column column = model.columns().get(index.getAndIncrement());
        if (columnFilter.test(column)) {
            // Only raise an exception if the column is actually requested.
            if (o == null && !isPrimaryKey && !parentNullable && !field.nullable()) {
                throw new SqlTemplateException("Non-null component '%s.%s' is null.".formatted(field.type().getSimpleName(), field.name()));
            }
            Object value = switch (o) {
                case Instant it -> Timestamp.from(it);
                case LocalDateTime it -> Timestamp.valueOf(it);
                case OffsetDateTime it -> Timestamp.from(it.toInstant());
                case LocalDate it -> Date.valueOf(it);
                case LocalTime it -> Time.valueOf(it);
                case Calendar it -> new Timestamp(it.getTimeInMillis());
                case java.util.Date it -> new Timestamp(it.getTime());
                case Enum<?> it -> switch (ofNullable(field.getAnnotation(DbEnum.class))
                        .map(DbEnum::value)
                        .orElse(NAME)) {
                    case NAME -> it.name();
                    case ORDINAL -> it.ordinal();
                };
                case null, default -> o;
            };
            return callback.apply(column, value);
        }
        return true;
    }
}
