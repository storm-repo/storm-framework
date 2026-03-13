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

import static java.util.Collections.nCopies;
import static java.util.List.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static st.orm.EnumType.NAME;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.ObjectMapperFactory.nullableHint;
import static st.orm.core.template.impl.RecordReflection.getDiscriminatorValue;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isJoinedEntity;
import static st.orm.core.template.impl.RecordReflection.isPolymorphicData;
import static st.orm.core.template.impl.RecordReflection.isRecord;
import static st.orm.core.template.impl.RecordReflection.isSealedEntity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import st.orm.Data;
import st.orm.DbEnum;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.Metamodel;
import st.orm.PK;
import st.orm.PersistenceException;
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

    /**
     * Caches field-name to component-index maps per concrete sealed subtype. Since the set of permitted subtypes is
     * small and fixed, this avoids rebuilding a HashMap on every row during sealed entity value extraction.
     */
    private static final ConcurrentMap<Class<?>, Map<String, Integer>> FIELD_INDEX_CACHE = new ConcurrentHashMap<>();

    private final RecordType recordType;
    private final Class<E> typeOverride;
    private final TableName tableName;
    private final List<RecordField> fields;
    private final List<Column> columns;
    private final RecordField primaryKeyField;
    private final List<Column> declaredColumns;
    private final Map<Class<? extends Data>, List<Metamodel<E, ?>>> mappedMetamodels;
    private final Metamodel<E, ID> primaryKeyMetamodel;
    private final Map<Metamodel<?, ?>, List<Column>> columnMap;

    /**
     * For sealed entity models, the 1-based column index of the discriminator column; -1 for non-sealed models.
     */
    private final int discriminatorColumnIndex;

    /**
     * Maps 1-based column indices to the sealed Data type for polymorphic FK discriminator columns.
     *
     * <p>A polymorphic FK (e.g., {@code Ref<Commentable>}) expands to two physical columns:
     * a discriminator column (e.g., {@code target_type}) and an FK id column (e.g., {@code target_id}).
     * This map identifies the discriminator columns so that
     * {@link #forEachValueOrdered(List, Data, BiConsumer)} can extract the discriminator value from the
     * {@link Ref#type()} instead of the {@link Ref#id()}.</p>
     */
    private final Map<Integer, Class<?>> polymorphicFkDiscriminatorColumns;

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
        this(recordType, null, tableName, fields, columns);
    }

    /**
     * Creates a model with an optional type override for sealed entity hierarchies.
     *
     * <p>When a sealed entity interface (e.g., {@code Vehicle}) uses single-table or joined inheritance,
     * the {@code recordType} is derived from the first permitted subclass (e.g., {@code Car}), but
     * {@link #type()} must return the sealed interface. The {@code typeOverride} provides this.</p>
     *
     * @param recordType the record type from which column metadata is derived.
     * @param typeOverride the type to return from {@link #type()}, or null to use the record type.
     * @param tableName the table name.
     * @param fields the record fields aligned with columns.
     * @param columns the column metadata.
     */
    public ModelImpl(@Nonnull RecordType recordType,
                     @Nullable Class<E> typeOverride,
                     @Nonnull TableName tableName,
                     @Nonnull List<RecordField> fields,
                     @Nonnull List<Column> columns) throws SqlTemplateException {
        assert fields.size() == columns.size() : "Columns and fields must have the same size";
        this.recordType = requireNonNull(recordType, "recordType");
        this.typeOverride = typeOverride;
        this.tableName = requireNonNull(tableName, "tableName");
        this.fields = copyOf(fields);
        this.columns = copyOf(columns);
        this.columnMap = initColumnMap(this.columns);
        this.discriminatorColumnIndex = initDiscriminatorColumnIndex(typeOverride);
        this.polymorphicFkDiscriminatorColumns = initPolymorphicFkDiscriminatorColumns(this.fields, this.columns);
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

    /**
     * For sealed entity models, returns the 1-based index of the discriminator column. The discriminator is always
     * the first column in a sealed entity model (index 1).
     */
    private static int initDiscriminatorColumnIndex(@Nullable Class<?> typeOverride) {
        return typeOverride != null && typeOverride.isSealed() && isSealedEntity(typeOverride) ? 1 : -1;
    }

    /**
     * Identifies polymorphic FK discriminator columns. For each polymorphic FK field (a {@code Ref} to a sealed
     * {@link Data} interface), the first of its two columns is the discriminator. This method maps those column
     * indices to the sealed type so that value extraction can produce the discriminator value instead of the FK id.
     */
    private static Map<Integer, Class<?>> initPolymorphicFkDiscriminatorColumns(
            List<RecordField> fields, List<Column> columns) {
        Map<Integer, Class<?>> map = new HashMap<>();
        RecordField previousField = null;
        for (int i = 0; i < fields.size(); i++) {
            RecordField field = fields.get(i);
            if (field.equals(previousField)) {
                // Second (or later) column for the same field; skip.
                previousField = field;
                continue;
            }
            previousField = field;
            if (!columns.get(i).foreignKey() || !Ref.class.isAssignableFrom(field.type())) {
                continue;
            }
            try {
                Class<? extends Data> refDataType = getRefDataType(field);
                if (isPolymorphicData(refDataType)) {
                    // First column of a polymorphic FK pair is the discriminator.
                    map.put(columns.get(i).index(), refDataType);
                }
            } catch (SqlTemplateException e) {
                // Should not happen for a valid model; skip gracefully.
            }
        }
        return map.isEmpty() ? Map.of() : Map.copyOf(map);
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
        return typeOverride != null ? typeOverride : (Class<E>) recordType.type();
    }

    @Override
    public boolean isJoinedInheritance() {
        return typeOverride != null && typeOverride.isSealed() && isJoinedEntity(typeOverride);
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
            if (metamodel.isInline()) {
                return getInlineColumns(metamodel);
            }
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

    /**
     * Resolves all declared columns that belong to the given inline record metamodel.
     *
     * <p>Inline records are not stored directly in the column map because they expand into their constituent
     * fields. This method collects all declared columns whose metamodel field path starts with the inline
     * record's field path prefix.</p>
     */
    private List<Column> getInlineColumns(@Nonnull Metamodel<?, ?> metamodel) throws SqlTemplateException {
        String prefix = metamodel.fieldPath() + ".";
        var inlineColumns = declaredColumns.stream()
                .filter(column -> column.metamodel().fieldPath().startsWith(prefix))
                .toList();
        if (inlineColumns.isEmpty()) {
            throw new SqlTemplateException("Column not found for inline metamodel: %s.".formatted(metamodel));
        }
        return inlineColumns;
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
    public void validateForeignKeys(@Nonnull List<Column> columns, @Nonnull E record) throws SqlTemplateException {
        requireNonNull(columns, "columns");
        requireNonNull(record, "record");
        for (var column : columns) {
            if (!column.foreignKey() || column.foreignKeyGeneration() == GenerationStrategy.NONE) {
                continue;
            }
            var value = getValue(column.metamodel(), record);
            if (value == null) {
                continue;
            }
            Object foreignKeyId;
            if (value instanceof Ref<?> ref) {
                foreignKeyId = ref.id();
            } else if (value instanceof Data data) {
                foreignKeyId = REFLECTION.getId(data);
            } else {
                continue;
            }
            if (foreignKeyId != null && REFLECTION.isDefaultValue(foreignKeyId)) {
                throw new PersistenceException(
                        "Foreign key '%s.%s' has a default primary key value. "
                                .formatted(record.getClass().getSimpleName(), column.name())
                        + "This typically indicates an unsaved entity is being used as a reference. "
                        + "Ensure the referenced entity has been persisted before using it as a foreign key.");
            }
        }
    }

    @Override
    public void forEachValue(@Nonnull Metamodel<E, ?> metamodel,
                             @Nonnull Object object,
                             @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        // For sealed entity models, columns originally share the same root metamodel, so
        // getColumns() could not distinguish between PK and non-PK columns. When a generated
        // metamodel is available (e.g., Animal_.name), its canonical form is registered via
        // a secondary metamodel on the column, enabling field-specific resolution.
        boolean resolvedViaInline = false;
        List<Column> columns;
        if (discriminatorColumnIndex > 0) {
            var mapped = metamodel.isColumn() ? columnMap.get(metamodel.canonical()) : null;
            columns = mapped != null
                    ? mapped
                    : declaredColumns.stream().filter(Column::primaryKey).toList();
        } else if (metamodel.isInline() && columnMap.get(metamodel.canonical()) == null) {
            // Inline record not directly in column map (e.g., Address). Fall back to inline resolution.
            columns = getInlineColumns(metamodel);
            resolvedViaInline = true;
        } else {
            columns = getColumns(metamodel);
        }
        if (resolvedViaInline) {
            forEachInlineValue(metamodel, columns, object, consumer);
            return;
        }
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
     * Extracts values for an inline record metamodel.
     *
     * <p>Inline records expand into their constituent fields in the column model. This method maps each column
     * back to its corresponding record component using the column's field path relative to the inline record,
     * handling both plain fields and foreign key fields.</p>
     */
    private void forEachInlineValue(@Nonnull Metamodel<E, ?> metamodel,
                                    @Nonnull List<Column> columns,
                                    @Nonnull Object object,
                                    @Nonnull BiConsumer<Column, Object> consumer) throws SqlTemplateException {
        if (object == null) {
            for (var column : columns) {
                consumer.accept(column, null);
            }
            return;
        }
        RecordType inlineRecordType = REFLECTION.getRecordType(object.getClass());
        List<? extends RecordField> inlineFields = inlineRecordType.fields();
        // Build a field-name to component-index map for the inline record.
        Map<String, Integer> fieldIndexMap = HashMap.newHashMap(inlineFields.size());
        for (int i = 0; i < inlineFields.size(); i++) {
            fieldIndexMap.put(inlineFields.get(i).name(), i);
        }
        String prefix = metamodel.fieldPath();
        for (var column : columns) {
            String columnFieldPath = column.metamodel().fieldPath();
            // Strip the inline prefix to get the relative field path within the inline record.
            String relativeField = columnFieldPath.substring(prefix.length() + 1);
            // The first segment of the relative field path is the component name.
            int dotIndex = relativeField.indexOf('.');
            String componentName = dotIndex >= 0 ? relativeField.substring(0, dotIndex) : relativeField;
            Integer componentIndex = fieldIndexMap.get(componentName);
            if (componentIndex == null) {
                throw new SqlTemplateException("No component '%s' found in inline record %s."
                        .formatted(componentName, object.getClass().getSimpleName()));
            }
            Object value = REFLECTION.getRecordValue(object, componentIndex);
            // Handle FK components: extract the primary key ID from the Data object.
            if (column.foreignKey() && value instanceof Data data) {
                value = REFLECTION.getId(data);
            }
            // Handle compound keys (e.g., compound FK or compound PK within the inline record).
            if (value != null && (column.primaryKey() || column.foreignKey()) && isRecord(value.getClass())) {
                value = REFLECTION.getRecordValue(value, column.keyIndex() - 1);
            }
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
        if (discriminatorColumnIndex > 0) {
            forEachSealedEntityValue(view, record, consumer);
            return;
        }
        int lastIndex = -1;
        int cachedGroupStart = -1;
        int cachedParamCount = -1;
        List<?> cachedValues = null;
        //noinspection ForLoopReplaceableByForEach
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
                throw new SqlTemplateException("Cannot write NULL to non-nullable column '%s'. Ensure the entity field has a value before inserting or updating, or %s if NULL is intended.".formatted(column.name(), nullableHint(type())));
            }
            if (column.foreignKey()) {
                if (value instanceof Ref<?> ref) {
                    Class<?> polymorphicSealedType = polymorphicFkDiscriminatorColumns.get(column.index());
                    // Polymorphic FK discriminator column: extract the discriminator value from the Ref's
                    // concrete type instead of the FK id.
                    value = polymorphicSealedType != null
                            ? getDiscriminatorValue(ref.type(), polymorphicSealedType)
                            : ref.id();
                } else if (value instanceof Data data) {
                    value = REFLECTION.getId(data);
                } else if (value != null) {
                    throw new SqlTemplateException("Invalid foreign key type for column '%s'. Expected a Ref or Data type, but got an unrecognized value type. Ensure the foreign key field is correctly typed.".formatted(column.name()));
                }
            }
            if (value != null && (column.primaryKey() || column.foreignKey()) && isRecord(value.getClass())) {
                value = REFLECTION.getRecordValue(value, column.keyIndex() - 1);
            }
            consumer.accept(column, map(column, value));
        }
    }

    /**
     * Sealed entity value extraction. For each column in the view:
     * <ul>
     *   <li>Discriminator column: emits the discriminator value derived from the record's concrete type.</li>
     *   <li>Data columns present in the concrete type: extracts the value via reflection.</li>
     *   <li>Data columns absent from the concrete type (subtype-specific): emits NULL.</li>
     * </ul>
     */
    private void forEachSealedEntityValue(@Nonnull List<Column> view,
                                           @Nonnull E record,
                                           @Nonnull BiConsumer<Column, Object> consumer)
            throws SqlTemplateException {
        assert typeOverride != null && typeOverride.isSealed();
        Class<?> concreteType = record.getClass();
        // Look up (or compute once) the field-name -> component-index map for the concrete type.
        Map<String, Integer> fieldIndexMap = FIELD_INDEX_CACHE.computeIfAbsent(concreteType, type -> {
            RecordType concreteRecordType = REFLECTION.getRecordType(type);
            var concreteFields = concreteRecordType.fields();
            Map<String, Integer> map = HashMap.newHashMap(concreteFields.size());
            for (int i = 0; i < concreteFields.size(); i++) {
                map.put(concreteFields.get(i).name(), i);
            }
            return Map.copyOf(map);
        });
        for (var column : view) {
            int index = column.index();
            if (index == discriminatorColumnIndex) {
                // Auto-populate discriminator value from the concrete type.
                consumer.accept(column, getDiscriminatorValue(concreteType, typeOverride));
                continue;
            }
            // Look up the field in the sealed model's field list (aligned with columns).
            RecordField modelField = fields.get(index - 1);
            Integer componentIndex = fieldIndexMap.get(modelField.name());
            if (componentIndex == null) {
                // Column belongs to a different subtype -> NULL.
                consumer.accept(column, null);
                continue;
            }
            Object value = REFLECTION.getRecordValue(record, componentIndex);
            if (column.foreignKey()) {
                if (value instanceof Ref<?> ref) {
                    value = ref.id();
                } else if (value instanceof Data data) {
                    value = REFLECTION.getId(data);
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
