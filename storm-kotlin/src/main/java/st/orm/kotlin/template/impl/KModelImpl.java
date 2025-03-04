/*
 * Copyright 2024 the original author or authors.
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
package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import st.orm.PersistenceException;
import st.orm.kotlin.template.KColumn;
import st.orm.kotlin.template.KModel;
import st.orm.template.SqlDialect;
import st.orm.template.impl.ColumnImpl;
import st.orm.template.impl.ModelImpl;
import st.orm.template.impl.TableName;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import static java.util.List.copyOf;

/**
 * Represents the model of an entity.
 */
public record KModelImpl<E extends Record, ID>(
        @Nonnull ModelImpl<E, ID> model,
        @Nonnull TableName tableName,
        @Nonnull KClass<E> type,
        @Nonnull KClass<ID> primaryKeyType,
        @Nonnull List<KColumn> columns) implements KModel<E, ID> {

    public KModelImpl {
        columns = copyOf(columns); // Defensive copy.
    }

    public KModelImpl(@Nonnull ModelImpl<E, ID> model) {
        this(
                model,
                model.tableName(),
                JvmClassMappingKt.getKotlinClass(model.type()),
                JvmClassMappingKt.getKotlinClass(model.primaryKeyType()),
                model.columns().stream()
                        .map(c -> new KColumnImpl((ColumnImpl) c))
                        .map(KColumn.class::cast)
                        .toList()
        );
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
        return model.isDefaultPrimaryKey(pk);
    }

    /**
     * Extracts the values from the given record and maps them to the columns of the entity or projection.
     *
     * @param record the record to extract the values from.
     * @return the values from the given record mapped to the columns of the entity or projection.
     * @throws PersistenceException if an error occurs while extracting the values.
     * @since 1.2
     */
    @Override
    public SequencedMap<KColumn, Object> getValues(@Nonnull E record) {
        var map = new LinkedHashMap<KColumn, Object>();
        model.getValues(record).forEach((c, v) -> map.put(new KColumnImpl((ColumnImpl) c), v));
        return map;
    }

    /**
     * Extracts the value for the specified column from the given record.
     *
     * @param column the column to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified column from the given record.
     * @throws PersistenceException if an error occurs while extracting the values.
     * @since 1.2
     */
    @Override
    public Object getValue(@Nonnull KColumn column, @Nonnull E record) {
        return model.getValue(((KColumnImpl) column).column(), record);
    }
}
