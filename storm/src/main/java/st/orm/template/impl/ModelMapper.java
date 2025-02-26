package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.template.Column;
import st.orm.template.Model;
import st.orm.template.SqlTemplateException;

import java.util.SequencedMap;
import java.util.function.Predicate;

/**
 * Maps a record to a set of columns.
 *
 * @param <T> the record type.
 * @param <ID> the primary key type.
 * @since 1.2
 */
public interface ModelMapper<T extends Record, ID> {

    /**
     * Creates a new instance of the model mapper for the given model.
     *
     * @param model the model to map.
     * @return a new instance of the model mapper.
     * @param <T> the record type.
     * @param <ID> the primary key type.
     */
    static <T extends Record, ID> ModelMapper<T, ID> of(@Nonnull Model<T, ID> model) {
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
        return map(record, _ -> true);
    }

    /**
     * Maps the values from the given record to a set of columns.
     *
     * @param record the record to map.
     * @param columnFilter the filter to determine which columns to include.
     * @return the values from the given record to a set of columns.
     * @throws SqlTemplateException if an error occurs while extracting the values.
     */
    SequencedMap<Column, Object> map(@Nonnull T record, @Nonnull Predicate<Column> columnFilter) throws SqlTemplateException;
}
