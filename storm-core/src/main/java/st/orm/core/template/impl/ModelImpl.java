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
import st.orm.DbEnum;
import st.orm.Ref;
import st.orm.core.spi.ORMConverter;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.mapping.RecordField;
import st.orm.core.template.SqlDialect;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;
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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static java.util.Collections.nCopies;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static st.orm.EnumType.NAME;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Represents the model of an entity or projection.
 *
 */
public final class ModelImpl<E extends Data, ID> implements Model<E, ID> {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final RecordType recordType;
    private final RecordField primaryKeyField;
    private final TableName tableName;
    private final List<Column> columns;
    private final List<RecordField> fields;
    private final List<ORMConverter> converters;

    public ModelImpl(@Nonnull RecordType recordType,
                     @Nullable RecordField primaryKeyField,
                     @Nonnull TableName tableName,
                     @Nonnull List<Column> columns,
                     @Nonnull List<RecordField> fields) {
        assert columns.size() == fields.size() : "Columns and fields must have the same size";
        this.recordType = requireNonNull(recordType, "recordType");
        this.tableName = requireNonNull(tableName, "tableName");
        this.columns = columns = copyOf(columns); // Defensive copy.
        this.fields = copyOf(fields); // Defensive copy.
        this.primaryKeyField = primaryKeyField;
        this.converters = new ArrayList<>(nCopies(columns.size(), null));
        for (int i = 0; i < columns.size(); i++) {
            ORMConverter converter = Providers.getORMConverter(fields.get(i)).orElse(null);
            if (converter != null) {
                converters.set(i, converter);
            }
        }
    }

    /**
     * Returns the schema, or empty String if the schema is not specified.
     *
     * @return the schema, or empty String if the schema is not specified.
     */
    @Override
    public String schema() {
        return tableName.schema();
    }

    /**
     * Returns the name of the table or view.
     *
     * @return the name of the table or view.
     */
    @Override
    public String name() {
        return tableName.name();
    }

    /**
     * Returns the qualified name of the table or view, including the schema and escape characters where necessary.
     *
     * @return the qualified name of the table or view, including the schema and escape characters where necessary.
     */
    @Override
    public String qualifiedName(@Nonnull SqlDialect dialect) {
        return tableName.getQualifiedName(dialect);
    }

    /**
     * Returns the type of the entity or projection.
     *
     * @return the type of the entity or projection.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Class<E> type() {
        return (Class<E>) recordType.type();
    }

    /**
     * Returns the type of the primary key.
     *
     * @return the type of the primary key.
     */
    @Override
    public Class<ID> primaryKeyType() {
        //noinspection unchecked
        return primaryKeyField == null ? (Class<ID>) Void.class : (Class<ID>) primaryKeyField.type();
    }

    /**
     * Returns {code true} if the specified primary key represents a default value, {@code false} otherwise.
     *
     * <p>This method is used to check if the primary key of the entity is a default value. This is useful when
     * determining if the entity is new or has been persisted before.</p>
     *
     * @param pk primary key to check.
     * @return {code true} if the specified primary key represents a default value, {@code false} otherwise.
     * @since 1.2
     */
    @Override
    public boolean isDefaultPrimaryKey(@Nullable ID pk) {
        return REFLECTION.isDefaultValue(pk);
    }

    /**
     * Extracts column values from the given record and feeds them to a consumer in model column order,
     * limited to columns accepted by {@code columnFilter}.
     *
     * <p>See {@link #forEachValue(Data, BiConsumer)} for details about ordering and the produced value types.
     * In short: the produced values are JDBC-ready and already converted (refs and foreign keys unpacked to ids,
     * Java time converted to JDBC time types).</p>
     *
     * @param record the record (entity or projection instance) to extract values from
     * @param filter predicate that decides whether a column should be visited
     * @param consumer receives each visited column together with its extracted (JDBC-ready) value
     * @throws SqlTemplateException if an error occurs during value extraction
     * @since 1.7
     */
    @Override
    public void forEachValue(@Nonnull E record, @Nonnull Predicate<Column> filter, @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        for (int i = 0; i < columns.size(); i++) {
            var column = columns.get(i);
            var converter = converters.get(column.index() - 1);
            if (converter != null) {
                int parameterCount = converter.getParameterCount();
                List<?> values = null;
                for (int j = 0; j < parameterCount; j++) {
                    if (!filter.test(column)) {
                        continue;
                    }
                    if (values == null) {
                        values = converter.toDatabase(record);
                    }
                    consumer.accept(column, values.get(j));
                }
                i += parameterCount - 1;
                continue;
            }
            if (!filter.test(column)) {
                continue;
            }
            var value = column.metamodel().getValue(record);
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
                if (value != null && isRecord(value.getClass())) {
                    value = REFLECTION.getRecordValue(value, column.keyIndex() - 1);
                }
            }
            Object mapped = switch (value) {
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
            consumer.accept(column, mapped);
        }
    }

    @Override
    @Nonnull
    public RecordType recordType() {
        return recordType;
    }

    @Nonnull
    public TableName tableName() {
        return tableName;
    }

    @Override
    @Nonnull
    public List<Column> columns() {
        return columns;
    }
}
