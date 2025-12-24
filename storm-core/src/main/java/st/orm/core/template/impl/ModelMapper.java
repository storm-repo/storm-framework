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
import st.orm.Metamodel;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.SqlTemplateException;

import java.util.Collection;
import java.util.SequencedMap;
import java.util.function.Predicate;

/**
 * Maps a record to a set of columns.
 *
 * @param <T> the record type.
 * @param <ID> the primary key type.
 * @since 1.2
 */
public interface ModelMapper<T extends Data, ID> {

    /**
     * Creates a new instance of the model mapper for the given model.
     *
     * @param model the model to map.
     * @return a new instance of the model mapper.
     * @param <T> the record type.
     * @param <ID> the primary key type.
     */
    static <T extends Data, ID> ModelMapper<T, ID> of(@Nonnull Model<T, ID> model) {
        return new ModelMapperImpl<>(model);
    }

    /**
     * Returns {@code true} if the given primary key is the default value, {@code false} otherwise.
     *
     * @param pk the primary key to check.
     * @return {@code true} if the given primary key is the default value, {@code false} otherwise.
     */
    boolean isDefaultValue(@Nullable ID pk);

    /**
     * Extracts the value for the specified column for the given record.
     *
     * @param column the column to extract the value for.
     * @param record the record to extract the value from.
     * @return the value for the specified column for the given record.
     * @throws SqlTemplateException if an error occurs while extracting the value.
     */
    Object map(@Nonnull Column column, @Nonnull T record) throws SqlTemplateException;

    /**
     * Maps the values from the given record to a set of columns.
     *
     * @param record the record to map.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    default SequencedMap<Column, Object> map(@Nonnull T record) throws SqlTemplateException{
        return map(record, ignore -> true);
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
    SequencedMap<Column, Object> map(@Nonnull T record,
                                     @Nonnull Collection<Metamodel<? extends T, ?>> fields,
                                     @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException;

    /**
     * Maps the values from the given record to a set of columns. The result is limited to the columns allowed by the
     * given filter.
     *
     * @param record the record to map.
     * @param columnFilter the filter to determine which columns to include.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    SequencedMap<Column, Object> map(@Nonnull T record,
                                     @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException;
}
