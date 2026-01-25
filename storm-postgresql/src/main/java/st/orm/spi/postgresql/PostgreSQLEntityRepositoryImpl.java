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
package st.orm.spi.postgresql;

import jakarta.annotation.Nonnull;
import st.orm.Data;
import st.orm.Metamodel;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.PreparedQuery;
import st.orm.core.repository.impl.EntityRepositoryImpl;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Query;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.LazySupplier;
import st.orm.Entity;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.partitioningBy;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.repository.impl.DirtySupport.getMaxShapes;
import static st.orm.core.repository.impl.StreamSupport.partitioned;
import static st.orm.core.template.Templates.table;
import static st.orm.core.template.SqlInterceptor.intercept;
import static st.orm.core.template.TemplateString.combine;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.impl.StringTemplates.flatten;

/**
 * Implementation of {@link EntityRepository} for PostgreSQL.
 */
public class PostgreSQLEntityRepositoryImpl<E extends Entity<ID>, ID>
        extends EntityRepositoryImpl<E, ID> {

    public PostgreSQLEntityRepositoryImpl(@Nonnull ORMTemplate ormTemplate, @Nonnull Model<E, ID> model) {
        super(ormTemplate, model);
    }

    private TemplateString getVersionString(@Nonnull Class<? extends Data> type, @Nonnull Column column) {
        TemplateString columnName = TemplateString.of(column.qualifiedName(ormTemplate.dialect()));
        TemplateString updateExpression = switch (column.type()) {
            case Class<?> c when Integer.TYPE.isAssignableFrom(c)
                    || Long.TYPE.isAssignableFrom(c)
                    || Integer.class.isAssignableFrom(c)
                    || Long.class.isAssignableFrom(c)
                    || BigInteger.class.isAssignableFrom(c) -> raw("\0.\0 + 1", table(type), columnName);
            case Class<?> c when Instant.class.isAssignableFrom(c)
                    || Date.class.isAssignableFrom(c)
                    || Calendar.class.isAssignableFrom(c)
                    || Timestamp.class.isAssignableFrom(c) -> TemplateString.of("CURRENT_TIMESTAMP");
            default ->
                    throw new PersistenceException("Unsupported version type: %s.".formatted(column.type().getSimpleName()));
        };
        return flatten(raw("\0 = \0", columnName, updateExpression));
    }

    /**
     * Constructs the PostgreSQL conflict clause for an upsert.
     * <p>
     * This method builds an "ON CONFLICT (<primary_keys>) DO UPDATE SET ..." clause.
     * For non-primary key columns, it assigns the value from the EXCLUDED pseudo‑table.
     * Version columns are updated using {@link #getVersionString(Class, Column)}.
     * </p>
     *
     * @param versionAware a flag that will be set if a version column is encountered.
     * @return the conflict clause as a TemplateString.
     */
    private TemplateString onConflictClause(@Nonnull AtomicBoolean versionAware) {
        var dialect = ormTemplate.dialect();
        // Determine the conflict target from primary key columns.
        String conflictTarget = model.declaredColumns().stream()
                .filter(Column::primaryKey)
                .map(c -> c.qualifiedName(dialect))
                .reduce("%s, %s"::formatted)
                .orElseThrow(() -> new PersistenceException("No primary key defined."));
        // Build the assignment list for non-primary key updatable columns.
        var assignments = model.declaredColumns().stream()
                .filter(not(Column::primaryKey))
                .filter(Column::updatable)
                .map(column -> {
                    if (column.version()) {
                        versionAware.setPlain(true);
                        return getVersionString(model.type(), column);
                    }
                    return TemplateString.of("%s = EXCLUDED.%s".formatted(column.qualifiedName(dialect), column.qualifiedName(dialect)));
                })
                .reduce((left, right) -> combine(left, TemplateString.of(", "), right))
                .map(st -> combine(TemplateString.of("DO UPDATE SET "), st))
                .orElse(TemplateString.of("DO NOTHING"));
        return flatten(combine(TemplateString.of("\nON CONFLICT ("), TemplateString.of(conflictTarget), raw(") \0", assignments)));
    }

    protected E validateUpsert(@Nonnull E entity) {
        if (isAutoGeneratedPrimaryKey() && !model.isDefaultPrimaryKey(entity.id())) {
            throw new PersistenceException("Primary key must not be set for auto-generated primary keys for upserts.");
        }
        return entity;
    }

    /**
     * Inserts or updates a single entity in the database.
     */
    @Override
    public void upsert(@Nonnull E entity) {
        if (isUpdate(entity)) {
            update(entity);
            return;
        }
        validateUpsert(entity);
        var versionAware = new AtomicBoolean();
        intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            var query = ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0""", model.type(), entity, onConflictClause(versionAware))));
            query.executeUpdate();
        });
    }

    /**
     * Inserts or updates a single entity in the database and returns its ID.
     */
    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        if (generationStrategy != SEQUENCE) {
            return upsertAndFetchIdNoSequence(entity);
        }
        if (isUpdate(entity)) {
            update(entity);
            return entity.id();
        }
        validateUpsert(entity);
        var versionAware = new AtomicBoolean();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            var query = ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0
                    RETURNING %s""".formatted(pkName), model.type(), entity, onConflictClause(versionAware))));
            return query.getSingleResult(model.primaryKeyType());
        });
    }

    private ID upsertAndFetchIdNoSequence(@Nonnull E entity) {
        if (isUpdate(entity)) {
            update(entity);
            return entity.id();
        }
        validateUpsert(entity);
        var versionAware = new AtomicBoolean();
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            try (var query = ormTemplate.query(flatten(raw("""
                        INSERT INTO \0
                        VALUES \0\0""", model.type(), entity, onConflictClause(versionAware)))).prepare()) {
                query.executeUpdate();
                if (isAutoGeneratedPrimaryKey()) {
                    try (var stream = query.getGeneratedKeys(model.primaryKeyType())) {
                        return stream.reduce((ignore1, ignore2) -> {
                            throw new NonUniqueResultException("Expected single result, but found more than one.");
                        }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
                    }
                }
                return entity.id();
            }
        });
    }

    /**
     * Inserts or updates a single entity in the database and returns the entity with its current state.
     */
    @Override
    public E upsertAndFetch(@Nonnull E entity) {
        return getById(upsertAndFetchId(entity));
    }

    /**
     * Inserts or updates a collection of entities in the database in batches.
     */
    @Override
    public void upsert(@Nonnull Iterable<E> entities) {
        upsert(toStream(entities), defaultBatchSize);
    }

    private sealed interface PartitionKey {}
    private static final class NoOpKey implements PartitionKey {
        private static final NoOpKey INSTANCE = new NoOpKey();
    }
    private static final class UpsertKey implements PartitionKey {
        private static final UpsertKey INSTANCE = new UpsertKey();
    }
    private record UpdateKey(@Nonnull Set<Metamodel<?, ?>> fields) implements PartitionKey {
        UpdateKey() {
            this(Set.of()); // All fields.
        }
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of their IDs.
     */
    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return upsertAndFetchIdsNoSequence(entities);
        }
        Map<Set<Metamodel<?, ?>>, PreparedQuery> updateQueries = new HashMap<>();
        try {
            var result = new ArrayList<ID>();
            var entityCache = entityCache();
            partitioned(toStream(entities), defaultBatchSize, entity -> {
                if (isUpdate(entity)) {
                    var dirty = getDirty(entity, entityCache.orElse(null));
                    if (dirty.isEmpty()) {
                        return NoOpKey.INSTANCE;
                    }
                    return new UpdateKey(dirty.get());
                } else {
                    return UpsertKey.INSTANCE;
                }
            }, getMaxShapes(), new UpdateKey()).forEach(partition -> {
                switch (partition.key()) {
                    case NoOpKey ignore -> result.addAll(partition.chunk().stream().map(E::id).toList());
                    case UpsertKey ignore -> result.addAll(getUpsertQuery(partition.chunk()).getResultList(model.primaryKeyType()));
                    case UpdateKey u -> result.addAll(updateAndFetchIds(partition.chunk(),
                            updateQueries.computeIfAbsent(u.fields(), ignore -> prepareUpdateQuery(u.fields())),
                            entityCache.orElse(null)));
                }
            });
            return result;
        } finally {
            closeQuietly(updateQueries.values().stream());
        }
    }

    private Query getUpsertQuery(@Nonnull Iterable<E> entities) {
        var versionAware = new AtomicBoolean();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () ->
                ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0
                    RETURNING %s""".formatted(pkName), model.type(), entities, onConflictClause(versionAware)))
                ));
    }

    private List<ID> upsertAndFetchIdsNoSequence(@Nonnull Iterable<E> entities) {
        Map<Set<Metamodel<?, ?>>, PreparedQuery> updateQueries = new HashMap<>();
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            var result = new ArrayList<ID>();
            var entityCache = entityCache();
            partitioned(toStream(entities), defaultBatchSize, entity -> {
                if (isUpdate(entity)) {
                    var dirty = getDirty(entity, entityCache.orElse(null));
                    if (dirty.isEmpty()) {
                        return NoOpKey.INSTANCE;
                    }
                    return new UpdateKey(dirty.get());
                } else {
                    return UpsertKey.INSTANCE;
                }
            }, getMaxShapes(), new UpdateKey()).forEach(partition -> {
                switch (partition.key()) {
                    case NoOpKey ignore -> result.addAll(partition.chunk().stream().map(E::id).toList());
                    case UpsertKey ignore -> result.addAll(upsertAndFetchIds(partition.chunk(), upsertQuery.get()));
                    case UpdateKey u -> result.addAll(updateAndFetchIds(partition.chunk(),
                            updateQueries.computeIfAbsent(u.fields(), ignore -> prepareUpdateQuery(u.fields())),
                            entityCache.orElse(null)));
                }
            });
            return result;
        } finally {
            closeQuietly(Stream.of(
                            updateQueries.values().stream(),
                            upsertQuery.value().stream()
                    )
                    .flatMap(identity()));
        }
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of the upserted entities.
     */
    @Override
    public List<E> upsertAndFetch(@Nonnull Iterable<E> entities) {
        return findAllById(upsertAndFetchIds(entities));
    }

    /**
     * Inserts or updates a stream of entities in the database in batches.
     */
    @Override
    public void upsert(@Nonnull Stream<E> entities) {
        upsert(entities, defaultBatchSize);
    }

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes.
     */
    @Override
    public void upsert(@Nonnull Stream<E> entities, int batchSize) {
        Map<Set<Metamodel<?, ?>>, PreparedQuery> updateQueries = new HashMap<>();
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            var entityCache = entityCache();
            partitioned(entities, batchSize, entity -> {
                if (isUpdate(entity)) {
                    var dirty = getDirty(entity, entityCache.orElse(null));
                    if (dirty.isEmpty()) {
                        return NoOpKey.INSTANCE;
                    }
                    return new UpdateKey(dirty.get());
                } else {
                    return UpsertKey.INSTANCE;
                }
            }, getMaxShapes(), new UpdateKey()).forEach(partition -> {
                switch (partition.key()) {
                    case NoOpKey ignore -> {}
                    case UpsertKey ignore -> upsert(partition.chunk(), upsertQuery.get());
                    case UpdateKey u -> update(partition.chunk(),
                            updateQueries.computeIfAbsent(u.fields(), ignore -> prepareUpdateQuery(u.fields())),
                            entityCache.orElse(null));
                }
            });
        } finally {
            closeQuietly(Stream.of(
                    updateQueries.values().stream(),
                    upsertQuery.value().stream()
            )
                    .flatMap(identity()));
        }
    }

    private boolean isUpdate(@Nonnull E entity) {
        return isAutoGeneratedPrimaryKey() && !model.isDefaultPrimaryKey(entity.id());
    }

    private Map<Boolean, List<E>> partition(@Nonnull List<E> entities) {
        return entities.stream().collect(partitioningBy(this::isUpdate));
    }

    private PreparedQuery prepareUpsertQuery() {
        var bindVars = ormTemplate.createBindVars();
        var versionAware = new AtomicBoolean();
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () ->
                ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0""", model.type(), bindVars, onConflictClause(versionAware)))
                ).prepare());
    }

    private void upsert(@Nonnull List<E> batch, @Nonnull PreparedQuery query) {
        if (batch.isEmpty()) {
            return;
        }
        batch.stream().map(this::validateUpsert).forEach(query::addBatch);
        int[] result = query.executeBatch();
        if (IntStream.of(result).anyMatch(r -> r != 0 && r != 1 && r != 2)) {
            throw new PersistenceException("Batch upsert failed.");
        }
    }

    private List<ID> upsertAndFetchIds(@Nonnull List<E> batch, @Nonnull PreparedQuery query) {
        if (batch.isEmpty()) {
            return List.of();
        }
        batch.stream().map(this::validateUpsert).forEach(query::addBatch);
        int[] result = query.executeBatch();
        if (IntStream.of(result).anyMatch(r -> r != 0 && r != 1 && r != 2)) {
            throw new PersistenceException("Batch upsert failed.");
        }
        if (isAutoGeneratedPrimaryKey()) {
            try (var generatedKeys = query.getGeneratedKeys(model.primaryKeyType())) {
                return generatedKeys.toList();
            }
        }
        return batch.stream().map(Entity::id).toList();
    }

    @Override
    public ID insertAndFetchId(@Nonnull E entity) {
        if (generationStrategy != SEQUENCE) {
            return super.insertAndFetchId(entity);
        }
        validateInsert(entity);
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        try (var query = ormTemplate.query(TemplateString.raw("""
                INSERT INTO \0
                VALUES \0
                RETURNING %s""".formatted(pkName), model.type(), entity)).prepare()) {
            return query.getSingleResult(model.primaryKeyType());
        }
    }

    @Override
    public List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.insertAndFetchIds(entities);
        }
        entities.forEach(this::validateInsert);
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        var query = ormTemplate.query(TemplateString.raw("""
            INSERT INTO \0
            VALUES \0
            RETURNING %s""".formatted(pkName), model.type(), entities));
        return query.getResultList(model.primaryKeyType());
    }
}