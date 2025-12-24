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
import static st.orm.core.template.impl.RecordReflection.getRecordType;
import static st.orm.core.template.impl.RecordReflection.getRefPkType;
import static st.orm.core.template.impl.RecordReflection.getRefDataType;
import static st.orm.core.template.impl.RecordReflection.isRecord;

/**
 * Factory for creating instances for record types.
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
            return Optional.of(wrapConstructor(
                    type,
                    refFactory,
                    transactionContext == null ? null : transactionContext.isReadOnly() ? null : transactionContext
            ));
        }
        return empty();
    }

    private record Compiled(@Nonnull ArgumentPlan plan, @Nonnull Class<?>[] parameterTypes) {}

    private static final ConcurrentMap<Class<?>, Compiled> COMPILED = new ConcurrentHashMap<>();

    private static Compiled compiledFor(@Nonnull RecordType type,
                                        @Nonnull RefFactory refFactory) throws SqlTemplateException {
        try {
            return COMPILED.computeIfAbsent(type.type(), t -> {
                try {
                    return new Compiled(compilePlan(type), expandParameterTypes(type, refFactory));
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
        EntityCache<Entity<?>, ?> entityCache;
        if (transactionContext != null && Entity.class.isAssignableFrom(type.type()) && getUpdateMode(type) != OFF) {
            //noinspection unchecked
            entityCache = (EntityCache<Entity<?>, ?>) transactionContext.entityCache((Class<? extends Entity<?>>) type.type());
        } else {
            entityCache = null;
        }
        var interner = new WeakInterner();
        return new ObjectMapper<>() {
            @Override
            public Class<?>[] getParameterTypes() {
                return compiled.parameterTypes();
            }

            @SuppressWarnings("unchecked")
            @Override
            public T newInstance(@Nonnull Object[] args) throws SqlTemplateException {
                Object[] adaptedArgs = compiled.plan()
                        .adapt(args, 0, false, refFactory, interner, transactionContext)
                        .constructorArgs();
                // Don't intern top level records.
                var record = ObjectMapperFactory.construct((Constructor<T>) type.constructor(), adaptedArgs, 0);
                if (entityCache != null) {
                    // Only intern entities when they need to be cached.
                    return (T) entityCache.intern((Entity<?>) record);
                }
                return record;
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

    private static final Pattern INT_PATTERN = Pattern.compile("\\d+");

    private static boolean isArgNull(@Nullable Object arg) {
        return arg == null;
    }

    /**
     * Compiled, reusable plan for adapting flat JDBC args into constructor args for a specific record type.
     */
    private interface ArgumentPlan {
        Result adapt(@Nonnull Object[] flatArgs,
                     int offset,
                     boolean parentNullable,
                     @Nonnull RefFactory refFactory,
                     @Nonnull WeakInterner interner,
                     @Nullable TransactionContext tx) throws SqlTemplateException;

        record Result(@Nonnull Object[] constructorArgs, int offset) {}
    }

    private interface Step {

        Object apply(@Nonnull Object[] flatArgs,
                     @Nonnull Offset offset,
                     boolean parentNullable,
                     @Nonnull RefFactory refFactory,
                     @Nonnull WeakInterner interner,
                     @Nullable TransactionContext tx) throws SqlTemplateException;

        /**
         * Mutable offset holder to avoid allocating pairs/results per step.
         */
        final class Offset {
            int i;
            Offset(int i) { this.i = i; }
        }
    }

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

    private static final class ConverterStep implements Step {
        private final Object converter; // keep the concrete type from Providers (compile-time type is whatever getORMConverter returns)
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
         * Small indirection so the compiled step can stay fast without reflection.
         * We wrap the real converter in an invoker during compilation.
         */
        @FunctionalInterface
        interface ConverterInvoker {
            Object fromDatabase(@Nonnull Object[] args, @Nonnull RefFactory refFactory) throws SqlTemplateException;
        }
    }

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
            return refFactory.create(dataType, pk);
        }
    }

    private static final class RecordStep implements Step {
        private final RecordField field;
        private final RecordType subType;
        private final ArgumentPlan subPlan;
        private final boolean subIsEntity;
        private final boolean subNeedsCache;

        private RecordStep(@Nonnull RecordField field, @Nonnull RecordType subType, @Nonnull ArgumentPlan subPlan) {
            this.field = field;
            this.subType = subType;
            this.subPlan = subPlan;
            this.subIsEntity = Entity.class.isAssignableFrom(subType.type());
            this.subNeedsCache = getUpdateMode(subType) != OFF;
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
            EntityCache<Entity<?>, ?> entityCache;
            if (context != null && subIsEntity && subNeedsCache) {
                //noinspection unchecked
                entityCache = (EntityCache<Entity<?>, ?>) context.entityCache((Class<? extends Entity<?>>) subType.type());
            } else {
                entityCache = null;
            }
            if (entityCache != null) {
                return entityCache.intern((Entity<?>) record);
            }
            return interner.intern(record);
        }
    }

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
                steps[i] = new RecordStep(field, sub, subPlan);
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
}