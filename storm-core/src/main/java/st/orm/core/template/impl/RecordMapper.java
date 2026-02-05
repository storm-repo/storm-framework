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
import st.orm.Entity;
import st.orm.EnumType;
import st.orm.DbEnum;
import st.orm.Ref;
import st.orm.core.spi.EntityCache;
import st.orm.core.spi.RefFactory;
import st.orm.core.spi.TransactionContext;
import st.orm.core.spi.WeakInterner;
import st.orm.core.template.SqlTemplateException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.lang.System.arraycopy;
import static java.util.Collections.addAll;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static st.orm.EnumType.NAME;
import static st.orm.UpdateMode.OFF;
import static st.orm.core.repository.impl.DirtySupport.getUpdateMode;
import static st.orm.core.spi.Providers.getORMConverter;
import static st.orm.core.template.impl.RecordReflection.findPkField;
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.getRefPkType;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Factory for creating {@link ObjectMapper} instances that construct Java records from JDBC result set columns.
 *
 * <p>This class handles the complex mapping from flat JDBC column arrays to nested record structures, including:</p>
 * <ul>
 *   <li>Recursive expansion of nested records</li>
 *   <li>Custom type converters via {@code @Convert} annotation</li>
 *   <li>Enum mapping (by name or ordinal)</li>
 *   <li>{@link Ref} creation for entity references</li>
 *   <li>Nullable field handling</li>
 * </ul>
 *
 * <h2>Compilation and Caching</h2>
 * <p>Record mapping plans are compiled once per record type and cached globally. The compilation produces:</p>
 * <ul>
 *   <li>An {@link ArgumentPlan} containing {@link Step} instances for each constructor parameter</li>
 *   <li>Expanded parameter types reflecting the flattened JDBC column structure</li>
 * </ul>
 *
 * <h2>Interning and Caching</h2>
 * <p>To ensure object identity consistency and reduce memory usage, constructed records are interned:</p>
 * <ul>
 *   <li><b>Entities within a transaction</b>: Interned via {@link EntityCache} (transaction-scoped)</li>
 *   <li><b>Other records and entities</b>: Interned via {@link WeakInterner} (query-scoped)</li>
 * </ul>
 *
 * <h3>Entity Cache Scoping</h3>
 * <p>The entity cache is transaction-scoped. Its behavior depends on the transaction isolation level:</p>
 * <ul>
 *   <li>At {@code REPEATABLE_READ} or higher: Cached instances are returned, providing object identity consistency</li>
 *   <li>At {@code READ_COMMITTED} or lower: Fresh data is fetched from the database on each read</li>
 * </ul>
 *
 * <p>The entity cache serves two purposes:</p>
 * <ul>
 *   <li><b>Dirty tracking</b>: The cached state serves as the baseline for detecting changes when updating entities
 *       (see {@link st.orm.DynamicUpdate}). Cache writes occur at all isolation levels when dirty tracking is enabled.</li>
 *   <li><b>Identity preservation</b>: At {@code REPEATABLE_READ}+, entities already in cache are returned directly,
 *       ensuring the same database row returns the same object instance within a transaction.</li>
 * </ul>
 *
 * <p>The entity cache is <em>not</em> available when there is no active transaction (e.g., {@code NOT_SUPPORTED}
 * propagation).</p>
 *
 * <h2>Early Cache Lookup Optimization</h2>
 * <p>For both top-level and nested entities, the mapper extracts the primary key directly from the flat column array
 * <em>before</em> constructing the entity or its nested objects. If a cached entity with that PK exists, construction
 * is skipped entirely, improving performance for queries that return duplicate entity references.</p>
 *
 * @see ObjectMapper
 * @see EntityCache
 * @see WeakInterner
 */
final class RecordMapper {

    private RecordMapper() {
    }

    /**
     * Returns a factory for creating instances of the specified record type.
     *
     * @param columnCount the number of columns to use as constructor arguments.
     * @param type the record type of the instance to create.
     * @param refFactory the factory for creating ref instances for entities and projections.
     * @return a factory for creating instances of the specified type.
     * @param <T> the type of the instance to create.
     * @throws SqlTemplateException if an error occurred while creating the factory.
     */
    static <T> Optional<ObjectMapper<T>> getFactory(int columnCount,
                                                    @Nonnull RecordType type,
                                                    @Nonnull RefFactory refFactory,
                                                    @Nullable TransactionContext transactionContext) throws SqlTemplateException {
        if (getParameterCount(type) == columnCount) {
            return Optional.of(wrapConstructor(type, refFactory, transactionContext));
        }
        return empty();
    }

    /**
     * Holds the compiled mapping plan and expanded parameter types for a record type.
     *
     * @param plan the compiled argument plan for adapting flat JDBC args to constructor args.
     * @param parameterTypes the expanded JDBC column types (flattened from nested records).
     */
    private record Compiled(@Nonnull ArgumentPlan plan, @Nonnull Class<?>[] parameterTypes, @Nonnull PkInfo pkInfo) {}

    /** Global cache of compiled plans, keyed by record class. Thread-safe for concurrent access. */
    private static final ConcurrentMap<Class<?>, Compiled> COMPILED = new ConcurrentHashMap<>();

    /**
     * Returns the compiled plan for the given record type, creating and caching it if necessary.
     *
     * @param type the record type to compile.
     * @param refFactory the factory for resolving Ref parameter types.
     * @return the compiled plan.
     * @throws SqlTemplateException if compilation fails.
     */
    private static Compiled compiledFor(@Nonnull RecordType type,
                                        @Nonnull RefFactory refFactory) throws SqlTemplateException {
        try {
            return COMPILED.computeIfAbsent(type.type(), t -> {
                try {
                    PkInfo pkInfo = Entity.class.isAssignableFrom(type.type())
                            ? calculatePkInfo(type)
                            : PkInfo.NONE;
                    return new Compiled(compilePlan(type), expandParameterTypes(type, refFactory), pkInfo);
                } catch (SqlTemplateException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SqlTemplateException ste) throw ste;
            throw e;
        }
    }

    /**
     * Returns the number of parameters for the specified record type. This method takes into account its components
     * recursively.
     *
     * @param type the record type to calculate the number of parameters for.
     * @return the number of parameters for the specified record type.
     */
    private static int getParameterCount(@Nonnull RecordType type) throws SqlTemplateException {
        int count = 0;
        for (RecordField field : type.fields()) {
            var converter = getORMConverter(field);
            if (converter.isPresent()) {
                count += converter.get().getParameterCount();
            } else {
                if (isRecord(field.type())) {
                    // Recursion for nested records.
                    count += getParameterCount(getRecordType(field.type()));
                } else {
                    count += 1; // Component of the record, count as one.
                }
            }
        }
        return count;
    }

    /**
     * Wraps the specified constructor in a factory.
     *
     * @param type the type holding the constructor to wrap.
     * @param refFactory the bridge for creating supplier instances for records.
     * @return a factory for creating instances using the specified constructor.
     * @param <T> the type of the instance to create.
     */
    private static <T> ObjectMapper<T> wrapConstructor(@Nonnull RecordType type,
                                                       @Nonnull RefFactory refFactory,
                                                       @Nullable TransactionContext transactionContext) throws SqlTemplateException {
        Compiled compiled = compiledFor(type, refFactory);
        boolean isEntity = Entity.class.isAssignableFrom(type.type());
        // Determine cache read/write policy.
        // Cache read: return cached instances (identity preservation) - only at REPEATABLE_READ+
        // Cache write: store for dirty tracking OR for identity preservation
        boolean cacheReadEnabled = transactionContext != null && transactionContext.isRepeatableRead();
        boolean dirtyTrackingEnabled = getUpdateMode(type) != OFF;
        boolean cacheWriteEnabled = cacheReadEnabled || dirtyTrackingEnabled;
        EntityCache<Entity<?>, ?> entityCache;
        if (transactionContext != null && isEntity && cacheWriteEnabled) {
            //noinspection unchecked
            entityCache = (EntityCache<Entity<?>, ?>) transactionContext.entityCache((Class<? extends Entity<?>>) type.type());
        } else {
            entityCache = null;
        }
        PkInfo pkInfo = compiled.pkInfo();
        var interner = new WeakInterner();
        return new ObjectMapper<>() {
            @Override
            public Class<?>[] getParameterTypes() {
                return compiled.parameterTypes();
            }

            @SuppressWarnings("unchecked")
            @Override
            public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                // Early cache lookup optimization for top-level entities.
                // If we can extract the PK early, check the cache before constructing nested objects.
                // Only perform cache lookup if cache read is enabled (identity preservation).
                if (entityCache != null && cacheReadEnabled && pkInfo.offset >= 0) {
                    Object pk = extractPk(args, pkInfo);
                    if (pk != null) {
                        //noinspection unchecked,rawtypes
                        Optional<Entity<?>> cached = ((EntityCache) entityCache).get(pk);
                        if (cached.isPresent()) {
                            // Cache hit - skip construction entirely.
                            return (T) cached.get();
                        }
                    }
                }
                Object[] adaptedArgs = compiled.plan()
                        .adapt(args, 0, false, refFactory, interner, transactionContext)
                        .constructorArgs();
                // Don't intern top level records.
                var record = ObjectMapperFactory.construct((Constructor<T>) type.constructor(), adaptedArgs, 0);
                if (entityCache != null) {
                    // Intern for dirty tracking and/or identity preservation.
                    Entity<?> interned = entityCache.intern((Entity<?>) record);
                    // Only return cached instance if cache read is enabled.
                    if (cacheReadEnabled) {
                        return (T) interned;
                    }
                }
                return record;
            }

            /**
             * Extracts the primary key from args at the configured offset.
             *
             * @param args the flat argument array.
             * @param pkInfo the PK offset and column count information.
             * @return the PK value, or null if any PK column is null or PK cannot be extracted.
             */
            private Object extractPk(@Nonnull Object[] args, @Nonnull PkInfo pkInfo) throws SqlTemplateException {
                int pkStart = pkInfo.offset;
                int pkColumnCount = pkInfo.columnCount;
                if (pkColumnCount == 1) {
                    // Simple PK - just return the value.
                    return args[pkStart];
                }
                // Composite PK - construct from columns.
                if (pkInfo.constructor == null) {
                    // Cannot construct composite PK without constructor.
                    return null;
                }
                Object[] pkArgs = new Object[pkColumnCount];
                for (int i = 0; i < pkColumnCount; i++) {
                    Object arg = args[pkStart + i];
                    if (arg == null) {
                        return null;  // Null in composite PK means no valid PK.
                    }
                    pkArgs[i] = arg;
                }
                return ObjectMapperFactory.construct(pkInfo.constructor, pkArgs, pkStart);
            }
        };
    }

    /**
     * Expands the specified parameter types to include the types of record components.
     *
     * @param type the record type that holds the constructor to expand the parameter types for.
     * @return the expanded parameter types.
     * @throws SqlTemplateException if an error occurred while expanding the parameter types.
     */
    private static Class<?>[] expandParameterTypes(@Nonnull RecordType type,
                                                   @Nonnull RefFactory refFactory) throws SqlTemplateException {
        List<Class<?>> expandedTypes = new ArrayList<>();
        var fields = type.fields();
        var parameterTypes = type.constructor().getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            var field = fields.get(i);
            var converter = getORMConverter(field);
            if (converter.isPresent()) {
                expandedTypes.addAll(converter.get().getParameterTypes());
                continue;
            }
            if (isRecord(parameterTypes[i])) {
                // Recursively expand record components.
                addAll(expandedTypes, expandParameterTypes(getRecordType(parameterTypes[i]), refFactory));
            } else if (Ref.class.isAssignableFrom(parameterTypes[i])) {
                // Ref type, add the parameterized type.
                expandedTypes.add(getRefPkType(fields.get(i)));
            } else {
                // Non-record type, add directly.
                expandedTypes.add(parameterTypes[i]);
            }
        }
        return expandedTypes.toArray(new Class<?>[0]);
    }

    /** Pattern for validating ordinal enum values. */
    private static final Pattern INT_PATTERN = Pattern.compile("\\d+");

    private static boolean isArgNull(@Nullable Object arg) {
        return arg == null;
    }

    /**
     * A compiled, reusable plan for adapting flat JDBC column values into constructor arguments.
     *
     * <p>An argument plan is compiled once per record type and cached. It transforms a flat array of JDBC
     * column values (in declaration order) into properly nested constructor arguments, handling type
     * conversion, nullable fields, and recursive record construction.</p>
     */
    private interface ArgumentPlan {

        /**
         * Adapts flat JDBC column values into constructor arguments for a record type.
         *
         * @param flatArgs the flat array of JDBC column values.
         * @param offset the starting offset into flatArgs.
         * @param parentNullable whether the parent context allows null values.
         * @param refFactory factory for creating {@link Ref} instances.
         * @param interner interner for deduplicating records and entities.
         * @param tx the transaction context, or null if not in a transaction.
         * @return the result containing constructor args and updated offset.
         * @throws SqlTemplateException if adaptation fails due to null constraint violations.
         */
        Result adapt(@Nonnull Object[] flatArgs,
                     int offset,
                     boolean parentNullable,
                     @Nonnull RefFactory refFactory,
                     @Nonnull WeakInterner interner,
                     @Nullable TransactionContext tx) throws SqlTemplateException;

        /**
         * The result of adapting flat args.
         *
         * @param constructorArgs the constructor arguments ready for record instantiation.
         * @param offset the updated offset into flatArgs after consuming this record's columns.
         */
        record Result(@Nonnull Object[] constructorArgs, int offset) {}
    }

    /**
     * A single step in the argument adaptation process, responsible for processing one constructor parameter.
     *
     * <p>Steps are composed into an {@link ArgumentPlan}. Each step type handles a specific kind of
     * constructor parameter:</p>
     * <ul>
     *   <li>{@link PlainStep}: Simple pass-through for primitive/simple types</li>
     *   <li>{@link ConverterStep}: Custom type conversion via {@code @Convert}</li>
     *   <li>{@link EnumStep}: Enum mapping by name or ordinal</li>
     *   <li>{@link RefStep}: Creates {@link Ref} instances for entity references</li>
     *   <li>{@link RecordStep}: Recursive construction of nested records/entities</li>
     * </ul>
     */
    private interface Step {

        /**
         * Applies this step to extract and transform a value from the flat args array.
         *
         * @param flatArgs the flat array of JDBC column values.
         * @param offset mutable offset tracker into flatArgs.
         * @param parentNullable whether the parent context allows null values.
         * @param refFactory factory for creating {@link Ref} instances.
         * @param interner interner for deduplicating records and entities.
         * @param tx the transaction context, or null if not in a transaction.
         * @return the processed value for this constructor parameter.
         * @throws SqlTemplateException if processing fails.
         */
        Object apply(@Nonnull Object[] flatArgs,
                     @Nonnull Offset offset,
                     boolean parentNullable,
                     @Nonnull RefFactory refFactory,
                     @Nonnull WeakInterner interner,
                     @Nullable TransactionContext tx) throws SqlTemplateException;

        /**
         * Mutable offset holder to track position in flatArgs across steps.
         *
         * <p>Using a mutable holder avoids allocating result pairs for each step.</p>
         */
        final class Offset {
            int i;
            Offset(int i) { this.i = i; }
        }
    }

    /**
     * Default implementation of {@link ArgumentPlan} that applies a sequence of steps.
     *
     * <p>Each step corresponds to one constructor parameter of the target record type.</p>
     */
    private static final class CompiledArgumentPlan implements ArgumentPlan {
        private final RecordType type;
        private final Step[] steps;

        private CompiledArgumentPlan(@Nonnull RecordType type, @Nonnull Step[] steps) {
            this.type = type;
            this.steps = steps;
        }

        @Override
        public Result adapt(@Nonnull Object[] flatArgs,
                            int offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext tx) throws SqlTemplateException {
            Object[] constructorArgs = new Object[steps.length];
            Step.Offset stepOffset = new Step.Offset(offset);
            for (int p = 0; p < steps.length; p++) {
                Object v = steps[p].apply(flatArgs, stepOffset, parentNullable, refFactory, interner, tx);
                RecordField field = type.fields().get(p);
                boolean nullable = parentNullable || field.nullable();
                if (!nullable && isArgNull(v)) {
                    throw new SqlTemplateException(
                            "Argument for non-null component '%s.%s' is null."
                                    .formatted(type.type().getSimpleName(), field.name())
                    );
                }
                constructorArgs[p] = v;
            }
            return new Result(constructorArgs, stepOffset.i);
        }
    }

    /**
     * Step that passes a single column value through unchanged.
     *
     * <p>Used for simple types (primitives, strings, etc.) that don't require conversion.</p>
     */
    private static final class PlainStep implements Step {
        @Override
        public Object apply(@Nonnull Object[] flatArgs,
                            @Nonnull Offset offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext context) {
            return flatArgs[offset.i++];
        }
    }

    /**
     * Step that applies a custom type converter to one or more columns.
     *
     * <p>Used for fields annotated with {@code @Convert}. The converter may consume multiple
     * columns (e.g., for composite types) as specified by its parameter count.</p>
     */
    private static final class ConverterStep implements Step {
        private final Object converter;
        private final int paramCount;

        private ConverterStep(Object converter, int paramCount) {
            this.converter = converter;
            this.paramCount = paramCount;
        }

        @Override
        public Object apply(@Nonnull Object[] flatArgs,
                            @Nonnull Offset offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext tx) throws SqlTemplateException {
            Object[] slice = new Object[paramCount];
            arraycopy(flatArgs, offset.i, slice, 0, paramCount);
            offset.i += paramCount;
            return ((ConverterInvoker) converter).fromDatabase(slice, refFactory);
        }

        /**
         * Functional interface for invoking converters without reflection at runtime.
         *
         * <p>The actual converter is wrapped in this interface during compilation.</p>
         */
        @FunctionalInterface
        interface ConverterInvoker {
            Object fromDatabase(@Nonnull Object[] args, @Nonnull RefFactory refFactory) throws SqlTemplateException;
        }
    }

    /**
     * Step that maps a column value to an enum constant.
     *
     * <p>Supports two mapping strategies via {@link DbEnum} annotation:</p>
     * <ul>
     *   <li>{@link EnumType#NAME}: Maps string values to enum constants by name (default)</li>
     *   <li>{@link EnumType#ORDINAL}: Maps integer values to enum constants by ordinal</li>
     * </ul>
     */
    private static final class EnumStep implements Step {
        private final Class<?> enumType;
        private final EnumType mapping;
        private final String ownerSimpleName;
        private final String fieldName;

        private EnumStep(Class<?> enumType, EnumType mapping, String ownerSimpleName, String fieldName) {
            this.enumType = enumType;
            this.mapping = mapping;
            this.ownerSimpleName = ownerSimpleName;
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(@Nonnull Object[] flatArgs,
                            @Nonnull Offset offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext context) throws SqlTemplateException {
            Object raw = flatArgs[offset.i++];
            Object v = switch (mapping) {
                case NAME -> raw;
                case ORDINAL -> {
                    if (raw == null) {
                        yield null;
                    }
                    if (raw instanceof String s && INT_PATTERN.matcher(s).matches()) {
                        yield Integer.parseInt(s);
                    }
                    throw new SqlTemplateException(
                            "Argument for ordinal enum component %s.%s is not valid."
                                    .formatted(ownerSimpleName, fieldName)
                    );
                }
            };
            return EnumMapper.getFactory(1, enumType).orElseThrow().newInstance(new Object[]{v});
        }
    }

    /**
     * Step that creates a {@link Ref} instance from a primary key column.
     *
     * <p>Refs are lazy references to entities or projections. The actual entity is not loaded
     * until the ref is dereferenced. This step consumes a single PK column and delegates to
     * {@link RefFactory} for ref creation and interning.</p>
     */
    private static final class RefStep implements Step {
        private final Class<? extends Data> dataType;

        private RefStep(@Nonnull Class<?> dataType) {
            @SuppressWarnings("unchecked")
            Class<? extends Data> dt = (Class<? extends Data>) dataType;
            this.dataType = dt;
        }

        @Override
        public Object apply(@Nonnull Object[] flatArgs,
                            @Nonnull Offset offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext context) {
            Object pk = flatArgs[offset.i++];
            if (pk == null) {
                return null;
            }
            return interner.intern(refFactory.create(dataType, pk));
        }
    }

    /**
     * Step that recursively constructs a nested record or entity from multiple columns.
     *
     * <p>This step handles the most complex case: nested record types that may themselves contain
     * further nested records. It delegates to a sub-{@link ArgumentPlan} for recursive construction.</p>
     *
     * <h2>Early Cache Lookup Optimization</h2>
     * <p>For entity types, this step can extract the primary key directly from the flat column array
     * <em>before</em> constructing the entity and its nested objects. If a cached entity with that PK
     * exists (in {@link EntityCache} or {@link WeakInterner}), construction is skipped entirely.</p>
     *
     * <p>This optimization is particularly valuable for queries that return duplicate entity references
     * (e.g., joins that repeat the same entity across multiple rows).</p>
     *
     * <h2>Interning</h2>
     * <p>After construction, entities are interned to ensure identity consistency:</p>
     * <ul>
     *   <li>Entities with dirty tracking: via {@link EntityCache} (transaction-scoped)</li>
     *   <li>Other entities and records: via {@link WeakInterner} (query-scoped)</li>
     * </ul>
     */
    private static final class RecordStep implements Step {
        private final RecordField field;
        private final RecordType subType;
        private final ArgumentPlan subPlan;
        private final boolean subIsEntity;
        private final boolean subNeedsCache;

        // Fields for early PK cache lookup optimization.

        /** Offset within this record's flatArgs where PK starts (-1 if not applicable). */
        private final int pkFlatOffset;
        /** Number of columns the PK spans. */
        private final int pkColumnCount;
        /** Total columns this record consumes (for skipping on cache hit). */
        private final int totalColumnCount;
        /** Constructor for composite PKs (null for simple single-column PKs). */
        private final Constructor<?> pkConstructor;

        private RecordStep(@Nonnull RecordField field,
                           @Nonnull RecordType subType,
                           @Nonnull ArgumentPlan subPlan,
                           int pkFlatOffset,
                           int pkColumnCount,
                           int totalColumnCount,
                           @Nullable Constructor<?> pkConstructor) {
            this.field = field;
            this.subType = subType;
            this.subPlan = subPlan;
            this.subIsEntity = Entity.class.isAssignableFrom(subType.type());
            this.subNeedsCache = getUpdateMode(subType) != OFF;
            this.pkFlatOffset = pkFlatOffset;
            this.pkColumnCount = pkColumnCount;
            this.totalColumnCount = totalColumnCount;
            this.pkConstructor = pkConstructor;
        }

        @Override
        public Object apply(@Nonnull Object[] flatArgs,
                            @Nonnull Offset offset,
                            boolean parentNullable,
                            @Nonnull RefFactory refFactory,
                            @Nonnull WeakInterner interner,
                            @Nullable TransactionContext context) throws SqlTemplateException {
            boolean nullableHere = parentNullable || field.nullable();
            int start = offset.i;
            // Determine cache read/write policy for nested entities.
            // Cache read: return cached instances (identity preservation) - only at REPEATABLE_READ+
            // Cache write: store for dirty tracking OR for identity preservation
            boolean cacheReadEnabled = context != null && context.isRepeatableRead();
            boolean cacheWriteEnabled = cacheReadEnabled || subNeedsCache;
            EntityCache<Entity<?>, ?> entityCache = null;
            if (context != null && subIsEntity && cacheWriteEnabled) {
                //noinspection unchecked
                entityCache = (EntityCache<Entity<?>, ?>) context.entityCache((Class<? extends Entity<?>>) subType.type());
            }
            if (subIsEntity && pkFlatOffset >= 0) {
                Object pk = extractPk(flatArgs, start + pkFlatOffset);
                if (pk != null) {
                    if (entityCache != null && cacheReadEnabled) {
                        // Cache read enabled: use EntityCache for transaction-scoped identity.
                        //noinspection unchecked,rawtypes
                        Optional<Entity<?>> cached = ((EntityCache) entityCache).get(pk);
                        if (cached.isPresent()) {
                            // Cache hit - skip construction entirely.
                            offset.i = start + totalColumnCount;
                            return cached.get();
                        }
                    } else {
                        // Cache read disabled or no entity cache: use WeakInterner for query-scoped identity.
                        //noinspection unchecked
                        Entity<?> cached = interner.get((Class<Entity<?>>) subType.type(), pk);
                        if (cached != null) {
                            // Cache hit - skip construction entirely.
                            offset.i = start + totalColumnCount;
                            return cached;
                        }
                    }
                }
            }
            ArgumentPlan.Result r = subPlan.adapt(flatArgs, offset.i, nullableHere, refFactory, interner, context);
            offset.i = r.offset();
            if (field.nullable()) {
                boolean allNull = true;
                for (int i = start; i < offset.i; i++) {
                    if (flatArgs[i] != null) {
                        allNull = false;
                        break;
                    }
                }
                if (allNull) {
                    return null;
                }
            }
            // Validate nested non-nullable components.
            Object[] childArgs = r.constructorArgs();
            var subFields = subType.fields();
            RecordField nullViolation = null;
            for (int j = 0; j < childArgs.length; j++) {
                if (isArgNull(childArgs[j]) && !subFields.get(j).nullable()) {
                    nullViolation = subFields.get(j);
                    break;
                }
            }
            if (nullViolation != null) {
                if (!nullableHere) {
                    throw new SqlTemplateException(
                            "Argument for non-null component '%s.%s' is null."
                                    .formatted(subType.type().getSimpleName(), nullViolation.name())
                    );
                }
                return null;
            }
            // Construct nested record.
            Object record = ObjectMapperFactory.construct(subType.constructor(), childArgs, start);
            if (entityCache != null) {
                // Intern for dirty tracking and/or identity preservation.
                Entity<?> interned = entityCache.intern((Entity<?>) record);
                if (cacheReadEnabled) {
                    // Return cached instance for transaction-scoped identity.
                    return interned;
                }
                // Cache read disabled: use WeakInterner for query-scoped identity only.
                // The entity was already stored in entityCache for dirty tracking.
                return interner.intern(record);
            }
            return interner.intern(record);
        }

        /**
         * Extracts the primary key from flatArgs at the given offset.
         *
         * @param flatArgs the flat argument array.
         * @param pkStart the starting offset for PK columns.
         * @return the PK value, or null if any PK column is null or PK cannot be extracted.
         */
        private Object extractPk(@Nonnull Object[] flatArgs, int pkStart) throws SqlTemplateException {
            if (pkColumnCount == 1) {
                // Simple PK - just return the value.
                return flatArgs[pkStart];
            }
            // Composite PK - construct from columns.
            if (pkConstructor == null) {
                // Cannot construct composite PK without constructor.
                return null;
            }
            Object[] pkArgs = new Object[pkColumnCount];
            for (int i = 0; i < pkColumnCount; i++) {
                Object arg = flatArgs[pkStart + i];
                if (arg == null) {
                    return null;  // Null in composite PK means no valid PK.
                }
                pkArgs[i] = arg;
            }
            return ObjectMapperFactory.construct(pkConstructor, pkArgs, pkStart);
        }
    }

    /**
     * Compiles an argument plan for the given record type.
     *
     * <p>This method analyzes the record's constructor parameters and creates an appropriate
     * {@link Step} for each one. The resulting plan can be reused across multiple row mappings.</p>
     *
     * <p>Step selection is based on parameter type:</p>
     * <ul>
     *   <li>Fields with {@code @Convert}: {@link ConverterStep}</li>
     *   <li>Nested records: {@link RecordStep} (recursive)</li>
     *   <li>Enums: {@link EnumStep}</li>
     *   <li>{@link Ref} types: {@link RefStep}</li>
     *   <li>All other types: {@link PlainStep}</li>
     * </ul>
     *
     * @param type the record type to compile a plan for.
     * @return the compiled argument plan.
     * @throws SqlTemplateException if compilation fails.
     */
    private static ArgumentPlan compilePlan(@Nonnull RecordType type) throws SqlTemplateException {
        Class<?>[] paramTypes = type.constructor().getParameterTypes();
        Step[] steps = new Step[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            RecordField field = type.fields().get(i);
            var converterOpt = getORMConverter(field);
            if (converterOpt.isPresent()) {
                var converter = converterOpt.get();
                int parameterCount = converter.getParameterCount();
                // Wrap converter call once to keep ConverterStep fast at runtime.
                ConverterStep.ConverterInvoker invoker = converter::fromDatabase;
                steps[i] = new ConverterStep(invoker, parameterCount);
                continue;
            }
            Class<?> p = paramTypes[i];
            if (isRecord(p)) {
                RecordType sub = getRecordType(p);
                ArgumentPlan subPlan = compilePlan(sub);
                // Calculate PK information for early cache lookup optimization.
                PkInfo pkInfo = calculatePkInfo(sub);
                int totalColumnCount = getParameterCount(sub);
                steps[i] = new RecordStep(field, sub, subPlan, pkInfo.offset, pkInfo.columnCount,
                        totalColumnCount, pkInfo.constructor);
            } else if (p.isEnum()) {
                EnumType enumType = ofNullable(field.getAnnotation(DbEnum.class)).map(DbEnum::value).orElse(NAME);
                steps[i] = new EnumStep(p, enumType, type.type().getSimpleName(), field.name());
            } else if (Ref.class.isAssignableFrom(p)) {
                steps[i] = new RefStep(getRefDataType(field));
            } else {
                steps[i] = new PlainStep();
            }
        }
        return new CompiledArgumentPlan(type, steps);
    }

    /**
     * Holds primary key location and construction information for an entity type.
     *
     * <p>This information enables the early cache lookup optimization in {@link RecordStep}.</p>
     *
     * @param offset the offset into flatArgs where the PK columns start (-1 if not applicable).
     * @param columnCount the number of columns the PK spans.
     * @param constructor the constructor for composite PKs (null for simple single-column PKs).
     */
    private record PkInfo(int offset, int columnCount, @Nullable Constructor<?> constructor) {
        /** Sentinel value indicating no PK information is available (non-entity types). */
        static final PkInfo NONE = new PkInfo(-1, 0, null);
    }

    /**
     * Calculates the primary key offset, column count, and constructor for the given record type.
     *
     * <p>This information enables early cache lookups by extracting the PK directly from flatArgs
     * before constructing nested objects.</p>
     *
     * @param type the record type to analyze.
     * @return PkInfo containing offset, column count, and constructor (for composite PKs).
     */
    private static PkInfo calculatePkInfo(@Nonnull RecordType type) throws SqlTemplateException {
        // Only entities have PKs.
        if (!Entity.class.isAssignableFrom(type.type())) {
            return PkInfo.NONE;
        }
        // Find the PK field.
        Optional<RecordField> pkFieldOpt = findPkField(type.type());
        if (pkFieldOpt.isEmpty()) {
            return PkInfo.NONE;
        }
        RecordField pkField = pkFieldOpt.get();
        // Calculate the offset: sum of column counts for all fields before the PK field.
        int offset = 0;
        for (RecordField field : type.fields()) {
            if (field.name().equals(pkField.name())) {
                break;
            }
            offset += getFieldColumnCount(field);
        }
        // Calculate how many columns the PK spans.
        int pkColumnCount = getFieldColumnCount(pkField);
        // For composite PKs (record types), we need the constructor.
        Constructor<?> pkConstructor = null;
        if (isRecord(pkField.type()) && pkColumnCount > 1) {
            pkConstructor = getRecordType(pkField.type()).constructor();
        }
        return new PkInfo(offset, pkColumnCount, pkConstructor);
    }

    /**
     * Returns the number of JDBC columns a field consumes in the flat args array.
     *
     * <p>This accounts for:</p>
     * <ul>
     *   <li>Custom converters that may consume multiple columns</li>
     *   <li>Nested records that expand to multiple columns recursively</li>
     *   <li>Simple fields that consume exactly one column</li>
     * </ul>
     *
     * @param field the field to calculate column count for.
     * @return the number of columns the field consumes.
     * @throws SqlTemplateException if the field type cannot be analyzed.
     */
    private static int getFieldColumnCount(@Nonnull RecordField field) throws SqlTemplateException {
        var converter = getORMConverter(field);
        if (converter.isPresent()) {
            return converter.get().getParameterCount();
        }
        if (isRecord(field.type())) {
            return getParameterCount(getRecordType(field.type()));
        }
        return 1;
    }
}