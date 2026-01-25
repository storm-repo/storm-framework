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
import st.orm.Data;
import st.orm.DbEnum;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.Ref;
import st.orm.core.spi.ORMConverter;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static java.util.Collections.nCopies;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static st.orm.EnumType.NAME;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Default {@link Model} implementation backed by {@link RecordType} and precomputed column metadata.
 *
 * <p>This implementation optimizes template operations by precomputing column mappings, converters,
 * and lookup tables up front.</p>
 *
 * <p><strong>Column iteration contract:</strong> {@link #forEachValue(List, Data, BiConsumer)} expects
 * the provided {@code columns} list to be ordered by {@link Column#index()} (typically originating from
 * {@link #columns()} or {@link #declaredColumns()}).</p>
 *
 * <p><strong>Converter subsets:</strong> if a logical field expands into multiple physical columns via an
 * {@link ORMConverter}, callers may provide any subset of those physical columns. Missing physical columns
 * are allowed as long as the provided list remains ordered.</p>
 *
 * @param <E> the entity/projection type.
 * @param <ID> the primary key type, or {@code Void} for projections without a primary key.
 */
public final class ModelImpl<E extends Data, ID> implements Model<E, ID> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final RecordType recordType;
    private final TableName tableName;
    private final List<RecordField> fields;
    private final List<Column> columns;
    private final RecordField primaryKeyField;
    private final List<Column> declaredColumns;
    private final Map<Class<? extends Data>, List<Metamodel<E, ?>>> mappedMetamodels;
    private final Metamodel<E, ID> primaryKeyMetamodel;
    private final Map<Metamodel<?, ?>, List<Column>> columnMap;

    /**
     * Converter aligned to {@link #columns()} by column index (1-based in {@link Column#index()}).
     *
     * <p>If a logical field expands into multiple physical columns, the converter is registered for each
     * physical column that belongs to that expansion.</p>
     */
    private final List<ORMConverter> converters;

    public ModelImpl(@Nonnull RecordType recordType,
                     @Nonnull TableName tableName,
                     @Nonnull List<RecordField> fields,
                     @Nonnull List<Column> columns) throws SqlTemplateException {
        assert fields.size() == columns.size() : "Columns and fields must have the same size";
        this.recordType = requireNonNull(recordType, "recordType");
        this.tableName = requireNonNull(tableName, "tableName");
        this.fields = copyOf(fields);
        this.columns = copyOf(columns);
        this.columnMap = initColumnMap(this.columns);
        this.converters = initConverters(this.fields, this.columns);
        this.primaryKeyField = initPrimaryKeyField(this.recordType);
        this.declaredColumns = initDeclaredColumns(this.columns);
        this.primaryKeyMetamodel = initPrimaryKeyMetamodel(this.declaredColumns);
        this.mappedMetamodels = initMappedMetamodels(this.fields, this.columns);
    }

    private static Map<Metamodel<?, ?>, List<Column>> initColumnMap(List<Column> columns) {
        var map = new HashMap<Metamodel<?, ?>, List<Column>>();
        for (var column : columns) {
            map.computeIfAbsent(column.metamodel().canonical(), ignore -> new ArrayList<>()).add(column);
            if (column.secondaryMetamodel() != null) {
                map.computeIfAbsent(column.secondaryMetamodel().canonical(), ignore -> new ArrayList<>()).add(column);
            }
        }
        map.replaceAll((k, v) -> List.copyOf(v));
        return Map.copyOf(map);
    }

    private static List<ORMConverter> initConverters(List<RecordField> fields, List<Column> columns) {
        var converters = new ArrayList<ORMConverter>(nCopies(columns.size(), null));
        for (int i = 0; i < columns.size(); i++) {
            var converter = getORMConverter(fields.get(i)).orElse(null);
            if (converter != null) {
                converters.set(i, converter);
            }
        }
        return converters;
    }

    private static RecordField initPrimaryKeyField(@Nonnull RecordType recordType) {
        for (var field : recordType.fields()) {
            if (field.isAnnotationPresent(PK.class)) {
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Data, ID> Metamodel<E, ID> initPrimaryKeyMetamodel(@Nonnull List<Column> declaredColumns) {
        var metamodels = declaredColumns.stream()
                .filter(Column::primaryKey)
                .map(Column::metamodel)
                .collect(toSet());
        assert metamodels.size() <= 1 : "More than one primary key metamodel found.";
        if (metamodels.isEmpty()) {
            return null;
        }
        return (Metamodel<E, ID>) metamodels.iterator().next();
    }

    private static List<Column> initDeclaredColumns(@Nonnull List<Column> columns) {
        List<Column> declared = new ArrayList<>();
        for (var column : columns) {
            if (column.metamodel().path().isEmpty()) {
                declared.add(column);
            }
        }
        return copyOf(declared);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Data> Map<Class<? extends Data>, List<Metamodel<E, ?>>> initMappedMetamodels(
            @Nonnull List<RecordField> fields,
            @Nonnull List<Column> columns
    ) throws SqlTemplateException {
        Map<Class<? extends Data>, List<Metamodel<E, ?>>> mapped = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            var field = fields.get(i);
            if (!field.isAnnotationPresent(FK.class)) {
                continue;
            }
            var type = field.type();
            if (Ref.class.isAssignableFrom(type)) {
                type = RecordReflection.getRefDataType(field);
            }
            var dataType = (Class<? extends Data>) type;
            var metamodel = (Metamodel<E, ?>) columns.get(i).metamodel();
            mapped.computeIfAbsent(dataType, ignore -> new ArrayList<>()).add(metamodel);
        }
        mapped.replaceAll((k, v) -> copyOf(v));
        return Map.copyOf(mapped);
    }

    @Override
    public Optional<Metamodel<E, ?>> findMetamodel(@Nonnull Class<? extends Data> type) {
        if (recordType.type().equals(type)) {
            return Optional.ofNullable(primaryKeyMetamodel);
        }
        var metamodels = mappedMetamodels.get(type);
        if (metamodels == null || metamodels.size() > 1) {
            return empty();
        }
        return Optional.of(metamodels.getFirst());
    }

    @Override
    public String schema() {
        return tableName.schema();
    }

    @Override
    public String name() {
        return tableName.name();
    }

    @Override
    public String qualifiedName(@Nonnull SqlDialect dialect) {
        return tableName.qualified(dialect);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<E> type() {
        return (Class<E>) recordType.type();
    }

    @Override
    public Class<ID> primaryKeyType() {
        //noinspection unchecked
        return primaryKeyField == null ? (Class<ID>) Void.class : (Class<ID>) primaryKeyField.type();
    }

    @Override
    public boolean isDefaultPrimaryKey(@Nullable ID pk) {
        return REFLECTION.isDefaultValue(pk);
    }

    @Override
    public Optional<Metamodel<E, ID>> getPrimaryKeyMetamodel() {
        return ofNullable(primaryKeyMetamodel);
    }

    @Override
    public List<Column> getColumns(@Nonnull Metamodel<?, ?> metamodel) throws SqlTemplateException {
        var columns = columnMap.get(metamodel.canonical());
        if (columns == null) {
            throw new SqlTemplateException("Column not found for metamodel: %s.".formatted(metamodel));
        }
        if (columns.size() > 1) {
            // Get fully qualified matches.
            var matches = columns.stream()
                    .filter(column -> column.metamodel().fieldPath().equals(metamodel.fieldPath()))
                    .toList();
            if (!matches.isEmpty()) {
                return matches;
            }
            if (columns.stream().map(Column::metamodel).map(Metamodel::fieldPath).distinct().count() > 1) {
                throw new SqlTemplateException("Ambiguous column mapping for metamodel: %s.".formatted(metamodel));
            }
        }
        return columns;
    }

    @Override
    public void forEachValue(@Nonnull List<Column> columns,
                             @Nonnull E record,
                             @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        requireNonNull(columns, "columns");
        requireNonNull(record, "record");
        requireNonNull(consumer, "consumer");
        forEachValueOrdered(columns, record, consumer);
    }

    @Override
    public void forEachValue(@Nonnull Metamodel<E, ?> metamodel,
                             @Nonnull Object object,
                             @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        var columns = getColumns(metamodel);
        Object value;
        if (object instanceof Data data) {
            value = REFLECTION.getId(data);
        } else {
            value = object;
        }
        if (value == null) {
            for (var column : columns) {
                consumer.accept(column, null);
            }
            return;
        }
        // We may check whether the value is compatible with the metamodel. Note that we need to check for primary-key
        // compatibility in the case of data classes. In this case we need to add the primary key type to the metamodel.
        if (isRecord(value.getClass())) {
            for (var column : columns) {
                consumer.accept(column, map(column, REFLECTION.getRecordValue(value, column.keyIndex() - 1)));
            }
        } else {
            if (columns.size() != 1) {
                throw new SqlTemplateException("Metamodel does not resolve to a single column: %s.".formatted(metamodel));
            }
            var column = columns.getFirst();
            consumer.accept(column, map(column, value));
        }
    }

    /**
     * Extracts values for an ordered list of columns.
     *
     * <p>This method supports ORM converters that expand a single logical field into multiple physical columns.
     * Converter-backed physical columns are assumed to be sequential in the model column list.</p>
     *
     * <p>The input list may omit physical columns from a converter group. Missing columns are allowed.
     * The only structural requirement is that the input list is ordered by {@link Column#index()}.</p>
     */
    private void forEachValueOrdered(@Nonnull List<Column> view,
                                     @Nonnull E record,
                                     @Nonnull BiConsumer<Column, Object> consumer)
            throws SqlTemplateException {
        int lastIndex = -1;
        int cachedGroupStart = -1;
        int cachedParamCount = -1;
        List<?> cachedValues = null;
        for (int i = 0, size = view.size(); i < size; i++) {
            var column = view.get(i);
            int index = column.index();
            if (lastIndex != -1 && index <= lastIndex) {
                throw new SqlTemplateException(
                        "Columns must be strictly ordered by model index. Got %d after %d."
                                .formatted(index, lastIndex)
                );
            }
            lastIndex = index;
            var converter = converters.get(index - 1);
            if (converter != null) {
                int parameterCount = converter.getParameterCount();
                int groupStart = findConverterGroupStart(index - 1, parameterCount);
                int groupStartIndex = groupStart + 1;
                if (groupStart != cachedGroupStart) {
                    cachedValues = converter.toDatabase(record);
                    cachedGroupStart = groupStart;
                    cachedParamCount = parameterCount;
                }
                assert cachedValues != null;
                if (cachedValues.size() != cachedParamCount) {
                    throw new SqlTemplateException(
                            "Converter for '%s' returned %d value(s), expected %d."
                                    .formatted(column.name(), cachedValues.size(), cachedParamCount)
                    );
                }
                int valueIndex = index - groupStartIndex;
                if (valueIndex < 0 || valueIndex >= cachedParamCount) {
                    throw new SqlTemplateException(
                            "Converter value index out of bounds for '%s': idx=%d start=%d count=%d."
                                    .formatted(column.name(), index, groupStartIndex, cachedParamCount)
                    );
                }
                consumer.accept(column, cachedValues.get(valueIndex));
                continue;
            }
            var value = getValue(column.metamodel(), record);
            if (value == null && !column.nullable() && !column.primaryKey()) {
                throw new SqlTemplateException("Column cannot be null: %s.".formatted(column.name()));
            }
            if (column.foreignKey()) {
                if (value instanceof Ref<?> ref) {
                    value = ref.id();
                } else if (value instanceof Data data) {
                    value = REFLECTION.getId(data);
                } else if (value != null) {
                    throw new SqlTemplateException("Invalid foreign key type for column: %s.".formatted(column.name()));
                }
            }
            if (value != null && (column.primaryKey() || column.foreignKey()) && isRecord(value.getClass())) {
                value = REFLECTION.getRecordValue(value, column.keyIndex() - 1);
            }
            consumer.accept(column, map(column, value));
        }
    }

    private Object getValue(@Nonnull Metamodel<Data, ?> metamodel, @Nonnull E record) throws SqlTemplateException {
        try {
            return metamodel.getValue(record);
        } catch (ClassCastException e) {
            throw new SqlTemplateException("Invalid data type: %s. Expected: %s."
                    .formatted(record.getClass().getSimpleName(), metamodel.root().getSimpleName()));
        }
    }

    /**
     * Determines the start index (0-based) of a converter group that contains {@code index}.
     *
     * <p>Converter-backed columns are sequential in the model. Given a column at {@code index} with parameter
     * count {@code n}, the group start must be within {@code [index - (n - 1), index]}.</p>
     *
     * <p>This method chooses the earliest possible start within that window that is still converter-backed.</p>
     */
    private int findConverterGroupStart(int index, int parameterCount) {
        int min = Math.max(0, index - (parameterCount - 1));
        int start = index;
        while (start > min && converters.get(start - 1) != null) {
            start--;
        }
        return start;
    }

    private Object map(@Nonnull Column column, @Nullable Object value) {
        return switch (value) {
            case Instant it -> Timestamp.from(it);
            case LocalDateTime it -> Timestamp.valueOf(it);
            case OffsetDateTime it -> Timestamp.from(it.toInstant());
            case LocalDate it -> Date.valueOf(it);
            case LocalTime it -> Time.valueOf(it);
            case Calendar it -> new Timestamp(it.getTimeInMillis());
            case java.util.Date it -> new Timestamp(it.getTime());
            case Enum<?> it -> switch (ofNullable(fields.get(column.index() - 1).getAnnotation(DbEnum.class))
                    .map(DbEnum::value)
                    .orElse(NAME)) {
                case NAME -> it.name();
                case ORDINAL -> it.ordinal();
            };
            case null, default -> value;
        };
    }

    @Override
    @Nonnull
    public RecordType recordType() {
        return recordType;
    }

    @Override
    @Nonnull
    public List<Column> columns() {
        return columns;
    }

    @Override
    public List<Column> declaredColumns() {
        return declaredColumns;
    }
}