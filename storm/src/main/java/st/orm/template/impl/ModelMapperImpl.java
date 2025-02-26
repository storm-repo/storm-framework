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
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
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
        map(record, model.type(), false, new AtomicInteger(), columnFilter, callback);
    }

    private boolean map(@Nonnull Record record,
                        @Nonnull Class<? extends Record> recordClass,
                        boolean lookupForeignKey,
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
                    if (!processLazyComponent(record, component, column, callback)) {
                        return false;
                    }
                    continue;
                }
                if (lookupForeignKey) {
                    return processLookupForeignKey(record, recordClass, component, column, index, columnFilter, callback);
                } else {
                    if (!processDefaultComponent(record, component, column, index, columnFilter, callback)) {
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

    private boolean processComponentWithConverter(@Nonnull Record record,
                                                  @Nonnull RecordComponent component,
                                                  @Nonnull ORMConverter converter,
                                                  @Nonnull AtomicInteger index,
                                                  @Nonnull Predicate<Column> columnFilter,
                                                  @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        var values = converter.getValues(record);
        if (values.isEmpty()) {
            throw new SqlTemplateException(STR."Converter returned no values for component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}'.");
        }
        for (var value : values) {
            Column col = model.columns().get(index.getAndIncrement());
            if (columnFilter.test(col)) {
                if (!callback.apply(col, value)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean processLazyComponent(@Nonnull Record record,
                                         @Nonnull RecordComponent component,
                                         @Nonnull Column column,
                                         @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (!REFLECTION.isAnnotationPresent(component, FK.class)) {
            throw new SqlTemplateException(STR."Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a foreign key.");
        }
        var id = Optional.ofNullable((Lazy<?, ?>) REFLECTION.invokeComponent(component, record))
                .map(Lazy::id)
                .orElse(null);
        if (id == null && REFLECTION.isNonnull(component)) {
            throw new SqlTemplateException(STR."Nonnull Lazy component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null.");
        }
        return callback.apply(column, id);
    }

    private boolean processLookupForeignKey(@Nonnull Record record,
                                            @Nonnull Class<? extends Record> recordClass,
                                            @Nonnull RecordComponent component,
                                            @Nonnull Column column,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        if (component.getType().isRecord() && !REFLECTION.isAnnotationPresent(component, FK.class)) {
            var r = (Record) REFLECTION.invokeComponent(component, record);
            if (r == null) {
                return true;
            }
            index.decrementAndGet(); // Reset index.
            if (!map(r, recordClass, true, index, columnFilter, callback)) {
                return false;
            }
        }
        if (REFLECTION.isAnnotationPresent(component, PK.class)) {
            var value = REFLECTION.invokeComponent(component, record);
            return callback.apply(column, value);   // We found the PK for the foreign key. Exit early.
        }
        return true;
    }

    private boolean processDefaultComponent(@Nonnull Record record,
                                            @Nonnull RecordComponent component,
                                            @Nonnull Column column,
                                            @Nonnull AtomicInteger index,
                                            @Nonnull Predicate<Column> columnFilter,
                                            @Nonnull BiFunction<Column, Object, Boolean> callback) throws Throwable {
        boolean isForeignKey = REFLECTION.isAnnotationPresent(component, FK.class);
        boolean isRecord = component.getType().isRecord();
        if (isForeignKey && !isRecord) {
            throw new SqlTemplateException(STR."Foreign key component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is not a record.");
        }
        if (isForeignKey || isRecord) {
            Record r = (Record) REFLECTION.invokeComponent(component, record);
            if (r == null) {
                if (REFLECTION.isNonnull(component)) {
                    String compType = isForeignKey ? "foreign key " : "";
                    throw new SqlTemplateException(STR."Nonnull \{compType}component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null."
                    );
                }
                return callback.apply(column, null);
            }
            index.decrementAndGet(); // Reset index for nested mapping.
            //noinspection unchecked
            return map(r, (Class<? extends Record>) component.getType(), isForeignKey, index, columnFilter, callback);
        } else {
            Object o = REFLECTION.invokeComponent(component, record);
            if (o == null && REFLECTION.isNonnull(component)) {
                throw new SqlTemplateException(STR."Nonnull component '\{component.getDeclaringRecord().getSimpleName()}.\{component.getName()}' is null."
                );
            }
            return callback.apply(column, o);
        }
    }
}
