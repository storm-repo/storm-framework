package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PK;
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
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.spi.Providers.getORMReflection;

/**
 * Maps a record to a model.
 *
 * @param <T> the record type.
 * @param <ID> the primary key type.
 * @since 1.2
 */
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

    private void map(@Nonnull Record record, @Nonnull Predicate<Column> columnFilter, @Nonnull BiFunction<Column, Object, Boolean> callback) throws SqlTemplateException {
        if (record.getClass() != model.type()) {
            throw new SqlTemplateException(STR."Record type not supported: \{record.getClass().getSimpleName()}. Expected: \{model.type().getSimpleName()}.");
        }
        map(record, model.type(), false, false, new AtomicInteger(), columnFilter, callback);
    }

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
                Column column = model.columns().get(index.getAndIncrement());
                if (!columnFilter.test(column)) {
                    continue;
                }
                if (Lazy.class.isAssignableFrom(component.getType())) {
                    if (!processLazyComponent(record, component, column, parentNullable, callback)) {
                        return false;
                    }
                    continue;
                }
                if (lookupForeignKey) {
                    return processLookupForeignKey(record, recordClass, component, column, parentNullable, index, columnFilter, callback);
                } else {
                    if (!processDefaultComponent(record, component, column, parentNullable, index, columnFilter, callback)) {
                        return false;
                    }
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

    private boolean processLazyComponent(@Nullable Record record,
                                         @Nonnull RecordComponent component,
                                         @Nonnull Column column,
                                         boolean parentNullable,
                                         @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
            throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
        }
        var id = ofNullable(record == null
                ? null
                : (Lazy<?, ?>) REFLECTION.invokeComponent(component, record))
                .map(Lazy::id)
                .orElse(null);
        if (id == null && !parentNullable && REFLECTION.isNonnull(component)) {
            throw new SqlTemplateException(STR."Nonnull Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
        }
        return callback.apply(column, id);
    }

    private boolean processLookupForeignKey(@Nullable Record record,
                                            @Nonnull Class<? extends Record> recordClass,
                                            @Nonnull RecordComponent component,
                                            @Nonnull Column column,
                                            boolean parentNullable,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (record == null) {
            return callback.apply(column, null);
        }
        if (component.getType().isRecord() && !REFLECTION.isAnnotationPresent(component, FK.class)) {
            var r = (Record) REFLECTION.invokeComponent(component, record);
            if (r == null) {
                return true;
            }
            index.decrementAndGet(); // Reset index.
            if (!map(r, recordClass, true, parentNullable || !REFLECTION.isNonnull(component), index, columnFilter, callback)) {
                return false;
            }
        }
        if (REFLECTION.isAnnotationPresent(component, PK.class)) {
            var value = REFLECTION.invokeComponent(component, record);
            return callback.apply(column, value);   // We found the PK for the foreign key. Exit early.
        }
        return true;
    }

    private boolean processDefaultComponent(@Nullable Record record,
                                            @Nonnull RecordComponent component,
                                            @Nonnull Column column,
                                            boolean parentNullable,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        boolean isForeignKey = REFLECTION.isAnnotationPresent(component, FK.class);
        boolean isRecord = component.getType().isRecord();
        if (isForeignKey && !isRecord) {
            throw new SqlTemplateException(STR."Foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a record.");
        }
        if (isForeignKey || isRecord) {
            Record r = record == null
                    ? null
                    : (Record) REFLECTION.invokeComponent(component, record);
            if (r == null) {
                if (!parentNullable && REFLECTION.isNonnull(component)) {
                    String compType = isForeignKey ? "foreign key " : "";
                    throw new SqlTemplateException(STR."Nonnull \{compType}component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
                }
                if (isForeignKey) {
                    // Skipping rest, as we're only interested in the foreign key.
                    return callback.apply(column, null);
                }
            }
            index.decrementAndGet(); // Reset index for nested mapping.
            //noinspection unchecked
            return map(r, (Class<? extends Record>) component.getType(), isForeignKey, parentNullable || !REFLECTION.isNonnull(component), index, columnFilter, callback);
        } else {
            Object o = record == null
                    ? null
                    : REFLECTION.invokeComponent(component, record);
            if (o == null && !parentNullable && REFLECTION.isNonnull(component)) {
                throw new SqlTemplateException(STR."Nonnull component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
            }
            return callback.apply(column, o);
        }
    }
}
