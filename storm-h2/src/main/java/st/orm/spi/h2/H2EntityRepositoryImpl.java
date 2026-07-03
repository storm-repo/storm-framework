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
package st.orm.spi.h2;

import static java.util.stream.Collectors.joining;
import static st.orm.GenerationStrategy.IDENTITY;
import static st.orm.GenerationStrategy.SEQUENCE;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.impl.MergeEntityRepositoryImpl;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.TemplateString;

/**
 * Implementation of {@link EntityRepository} for H2.
 */
public class H2EntityRepositoryImpl<E extends Entity<ID>, ID> extends MergeEntityRepositoryImpl<E, ID> {

    /**
     * H2 types used to cast bound parameters in the MERGE source query, keyed by the Java type of the column.
     * The temporal mappings follow how the ORM binds values: Instant, OffsetDateTime and ZonedDateTime are bound
     * as {@link Timestamp}, so they cast to TIMESTAMP. BigDecimal casts to DECFLOAT rather than NUMERIC, as a
     * bare NUMERIC in H2 has scale 0 and would truncate the fraction.
     */
    private static final Map<Class<?>, String> CAST_TYPES = Map.ofEntries(
            Map.entry(String.class, "VARCHAR"),
            Map.entry(char.class, "CHAR"),
            Map.entry(Character.class, "CHAR"),
            Map.entry(boolean.class, "BOOLEAN"),
            Map.entry(Boolean.class, "BOOLEAN"),
            Map.entry(byte.class, "TINYINT"),
            Map.entry(Byte.class, "TINYINT"),
            Map.entry(short.class, "SMALLINT"),
            Map.entry(Short.class, "SMALLINT"),
            Map.entry(int.class, "INTEGER"),
            Map.entry(Integer.class, "INTEGER"),
            Map.entry(long.class, "BIGINT"),
            Map.entry(Long.class, "BIGINT"),
            Map.entry(float.class, "REAL"),
            Map.entry(Float.class, "REAL"),
            Map.entry(double.class, "DOUBLE PRECISION"),
            Map.entry(Double.class, "DOUBLE PRECISION"),
            Map.entry(BigDecimal.class, "DECFLOAT"),
            Map.entry(BigInteger.class, "DECFLOAT"),
            Map.entry(byte[].class, "VARBINARY"),
            Map.entry(UUID.class, "UUID"),
            Map.entry(LocalDate.class, "DATE"),
            Map.entry(LocalTime.class, "TIME"),
            Map.entry(LocalDateTime.class, "TIMESTAMP"),
            Map.entry(OffsetTime.class, "TIME WITH TIME ZONE"),
            Map.entry(Instant.class, "TIMESTAMP"),
            Map.entry(OffsetDateTime.class, "TIMESTAMP"),
            Map.entry(ZonedDateTime.class, "TIMESTAMP"),
            Map.entry(java.sql.Date.class, "DATE"),
            Map.entry(java.sql.Time.class, "TIME"),
            Map.entry(Timestamp.class, "TIMESTAMP"));

    public H2EntityRepositoryImpl(@Nonnull ORMTemplate ormTemplate, @Nonnull Model<E, ID> model) {
        super(ormTemplate, model);
    }

    /**
     * Returns the H2 type used to cast a bound parameter of the given column, or {@code null} when no mapping is
     * known — the parameter is left uncast in that case.
     *
     * <p>H2 cannot infer the type of a bare {@code ?} in the projection of the MERGE source query and fails with
     * "Unknown data type". Casting each parameter gives the parser the missing type information.</p>
     */
    @Nullable
    @Override
    protected String castType(@Nonnull Column column) {
        Class<?> type = column.type();
        if (column.foreignKey()) {
            var secondary = column.secondaryMetamodel();
            if (secondary == null) {
                return null;
            }
            // Foreign key columns store the referenced entity's primary key. For compound keys the foreign key
            // spans multiple columns; keyIndex identifies this column's position within the flattened key.
            var leaves = new ArrayList<Class<?>>();
            flattenKeyTypes(secondary.fieldType(), leaves);
            int index = column.keyIndex();
            if (index < 1 || index > leaves.size()) {
                return null;
            }
            type = leaves.get(index - 1);
        }
        String mapped = CAST_TYPES.get(type);
        if (mapped != null) {
            return mapped;
        }
        if (type.isEnum()) {
            return "VARCHAR";   // Enums are bound by name.
        }
        if (Date.class.isAssignableFrom(type) || Calendar.class.isAssignableFrom(type)) {
            return "TIMESTAMP";
        }
        return null;
    }

    /**
     * Flattens a key type into the Java types of its database columns, in declaration order: records recurse into
     * their components, entity references resolve to their primary key, and simple types are the leaves.
     */
    private static void flattenKeyTypes(@Nonnull Class<?> type, @Nonnull List<Class<?>> leaves) {
        if (type.isRecord()) {
            if (Data.class.isAssignableFrom(type)) {
                for (RecordComponent component : type.getRecordComponents()) {
                    if (component.isAnnotationPresent(PK.class)) {
                        flattenKeyTypes(component.getType(), leaves);
                        return;
                    }
                }
                leaves.add(type);   // No primary key found; unmappable.
                return;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                flattenKeyTypes(component.getType(), leaves);
            }
            return;
        }
        leaves.add(type);
    }

    @Override
    protected TemplateString mergeInsert() {
        var dialect = ormTemplate.dialect();
        var insertDuplicates = new HashSet<>();
        var insertArgs = model.declaredColumns().stream()
                .filter(c -> !c.primaryKey() || c.generation() != IDENTITY)
                .filter(column -> insertDuplicates.add(column.name()))
                .map(c -> c.qualifiedName(dialect))
                .toList();
        var valuesDuplicates = new HashSet<>();
        var valuesArgs = model.declaredColumns().stream()
                .filter(c -> !c.primaryKey() || c.generation() != IDENTITY)
                .filter(column -> valuesDuplicates.add(column.name()))
                .map(c -> {
                    if (!c.sequence().isEmpty()) {
                        return "NEXT VALUE FOR %s".formatted(dialect.getSafeIdentifier(c.sequence()));
                    }
                    return "src.%s".formatted(c.qualifiedName(dialect));
                })
                .toList();
        assert insertArgs.size() == valuesArgs.size();
        if (insertArgs.isEmpty()) {
            return TemplateString.EMPTY;
        }
        String insertSql = insertArgs.stream().collect(joining(", ", "INSERT (", ")"));
        String valuesSql = valuesArgs.stream().collect(joining(", ", "VALUES (", ")"));
        String sql = "\n\t%s\n\t%s".formatted(insertSql, valuesSql);
        return TemplateString.of("\nWHEN NOT MATCHED THEN%s".formatted(sql));
    }

    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (isAutoGeneratedPrimaryKey() && generationStrategy == SEQUENCE) {
            throw new PersistenceException("H2 does not support using sequence-based ID generation together with fetch mode for upserts.");
        }
        return super.upsertAndFetchIds(entities);
    }

    @Override
    public ID insertAndFetchId(@Nonnull E entity) {
        if (generationStrategy == SEQUENCE) {
            throw new PersistenceException("H2 does not support using sequence-based ID generation together with fetch mode.");
        }
        return super.insertAndFetchId(entity);
    }

    @Override
    public List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy == SEQUENCE) {
            throw new PersistenceException("H2 does not support using sequence-based ID generation together with fetch mode.");
        }
        return super.insertAndFetchIds(entities);
    }
}
