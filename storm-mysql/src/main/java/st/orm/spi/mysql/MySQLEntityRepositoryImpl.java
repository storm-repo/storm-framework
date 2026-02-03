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
package st.orm.spi.mysql;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Metamodel;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.PreparedQuery;
import st.orm.core.spi.EntityCache;
import st.orm.core.repository.impl.EntityRepositoryImpl;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static st.orm.GenerationStrategy.IDENTITY;
import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.repository.impl.DirtySupport.getMaxShapes;
import static st.orm.core.repository.impl.StreamSupport.partitioned;
import static st.orm.core.template.SqlInterceptor.intercept;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.impl.StringTemplates.flatten;

/**
 * Implementation of {@link EntityRepository} for MySQL.
 */
public class MySQLEntityRepositoryImpl<E extends Entity<ID>, ID>
        extends EntityRepositoryImpl<E, ID> {

    public MySQLEntityRepositoryImpl(@Nonnull ORMTemplate ormTemplate, @Nonnull Model<E, ID> model) {
        super(ormTemplate, model);
    }

    private String getVersionString(@Nonnull Column column) {
        String columnName = column.qualifiedName(ormTemplate.dialect());
        String updateExpression = switch (column.type()) {
            case Class<?> c when Integer.TYPE.isAssignableFrom(c)
                        || Long.TYPE.isAssignableFrom(c)
                        || Integer.class.isAssignableFrom(c)
                        || Long.class.isAssignableFrom(c)
                        || BigInteger.class.isAssignableFrom(c) -> "%s + 1".formatted(columnName);
            case Class<?> c when Instant.class.isAssignableFrom(c)
                        || Date.class.isAssignableFrom(c)
                        || Calendar.class.isAssignableFrom(c)
                        || Timestamp.class.isAssignableFrom(c) -> "CURRENT_TIMESTAMP";
            default ->
                    throw new PersistenceException("Unsupported version type: %s.".formatted(column.type().getSimpleName()));
        };
        return "%s = %s".formatted(columnName, updateExpression);
    }

    protected TemplateString onDuplicateKey(@Nonnull AtomicBoolean versionAware) {
        var dialect = ormTemplate.dialect();
        var values = new ArrayList<String>();
        var duplicates = new HashSet<>();   // CompoundPks may also have their columns included as stand-alone fields. Only include them once.
        // LAST_INSERT_ID() is used to get the last auto-generated primary key value in case no fields are updated.
        model.declaredColumns().stream()
                .filter(Column::primaryKey)
                .filter(column -> duplicates.add(column.name()))
                .map(column -> {
                    String columnName = column.qualifiedName(dialect);
                    if (column.generation() == IDENTITY || (column.generation() == SEQUENCE && column.sequence().isEmpty())) {
                        return "%s = LAST_INSERT_ID(%s)".formatted(columnName, columnName);
                    }
                    return "%s = VALUES(%s)".formatted(columnName, columnName);
                })
                .forEach(values::add);
        model.declaredColumns().stream()
                .filter(not(Column::primaryKey))
                .filter(Column::updatable)
                .filter(column -> duplicates.add(column.name()))
                .map(column -> {
                    if (column.version()) {
                        versionAware.setPlain(true);
                        return getVersionString(column);
                    }
                    String columnName = column.qualifiedName(dialect);
                    return "%s = VALUES(%s)".formatted(columnName, columnName);
                })
                .forEach(values::add);
        if (values.isEmpty()) {
            return TemplateString.EMPTY;
        }
        return TemplateString.of("\nON DUPLICATE KEY UPDATE %s".formatted(String.join(", ", values)));
    }

    protected E validateUpsert(@Nonnull E entity) {
        if (isAutoGeneratedPrimaryKey() && !model.isDefaultPrimaryKey(entity.id())) {
            throw new PersistenceException("Primary key must not be set for auto-generated primary keys for upserts.");
        }
        return entity;
    }

    /**
     * Inserts or updates a single entity in the database.
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted. If it does exist, it will be updated to reflect the current state of
     * the entity. This approach ensures that the entity is either created or brought up-to-date, depending on
     * its existence in the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    @Override
    public void upsert(@Nonnull E entity) {
        if (isUpdate(entity)) {
            update(entity);
            return;
        }
        validateUpsert(entity);
        entityCache().ifPresent(cache -> {
            if (model.isDefaultPrimaryKey(entity.id())) {
                // Mysql is able to update a record with the same unique key so we need to clear the cache as we cannot
                // predict which record is updated.
                cache.clear();
            } else {
                cache.remove(entity.id());
            }
        });
        var versionAware = new AtomicBoolean();
        intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
             var query = ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0""", model.type(), entity, onDuplicateKey(versionAware)))).managed();
            query.executeUpdate();
        });
    }

    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        if (generationStrategy != SEQUENCE) {
            return upsertAndFetchIdNoSequence(entity);
        }
        throw new PersistenceException("MySQL does not support sequence-based generation.");
    }

    /**
     * Inserts or updates a single entity in the database and returns its ID.
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted; if it exists, it will be updated. Upon successful completion,
     * the method returns the ID of the entity as stored in the database. This approach ensures that the entity
     * is either created or brought up-to-date, depending on its existence in the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @return the ID of the upserted entity, reflecting its identifier in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    private ID upsertAndFetchIdNoSequence(@Nonnull E entity) {
        if (isUpdate(entity)) {
            update(entity);
            return entity.id();
        }
        validateUpsert(entity);
        entityCache().ifPresent(cache -> {
            if (model.isDefaultPrimaryKey(entity.id())) {
                // MySQL can update a record with the same unique key so we need to clear the cache
                // as we cannot predict which record is updated.
                cache.clear();
            } else {
                cache.remove(entity.id());
            }
        });
        var versionAware = new AtomicBoolean();
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            try (var query = ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0""", model.type(), entity, onDuplicateKey(versionAware)))).managed().prepare()) {
                query.executeUpdate();
                if (isAutoGeneratedPrimaryKey()) {
                    try (var stream = query.getGeneratedKeys(model.primaryKeyType())) {
                        return stream
                                .reduce((ignore1, ignore2) -> {
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
     *
     * <p>This method performs an "upsert" operation on the provided entity. If the entity does not already exist
     * in the database, it will be inserted; if it exists, it will be updated. Upon successful completion,
     * the method returns the entity as it exists in the database after the upsert operation. This ensures that
     * the returned entity reflects any modifications applied during the upsert process, such as generated primary keys,
     * updated timestamps, or default values set by the database.</p>
     *
     * @param entity the entity to be inserted or updated. The entity must be non-null and contain valid data
     *               for insertion or update in the database.
     * @return the upserted entity, reflecting its current state in the database. This includes any
     *         database-applied changes, such as primary key assignments, default values, or timestamp updates.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    @Override
    public E upsertAndFetch(@Nonnull E entity) {
        return getById(upsertAndFetchId(entity));
    }

    /**
     * Inserts or updates a collection of entities in the database in batches.
     *
     * <p>This method processes the provided entities in batches, optimizing performance for larger collections by
     * reducing database overhead. For each entity, the method performs an "upsert" operation, meaning it will insert
     * the entity if it does not already exist in the database, or update it if it does. This approach ensures that
     * the entities are either created or brought up-to-date, depending on their existence in the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection must
     *                 be non-null and contain valid data for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    @Override
    public void upsert(@Nonnull Iterable<E> entities) {
        upsert(toStream(entities), defaultBatchSize);
    }

    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return upsertAndFetchIdsNoSequence(entities);
        }
        throw new PersistenceException("MySQL does not support sequence-based generation.");
    }

    public sealed interface PartitionKey {}
    public static final class NoOpKey implements PartitionKey {
        public static final NoOpKey INSTANCE = new NoOpKey();
    }
    public static final class UpsertKey implements PartitionKey {
        public static final UpsertKey INSTANCE = new UpsertKey();
    }
    public record UpdateKey(@Nonnull Set<Metamodel<?, ?>> fields) implements PartitionKey {
        public UpdateKey() {
            this(Set.of()); // All fields.
        }
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of their IDs.
     *
     * <p>This method processes the provided entities in batches to optimize performance for larger collections,
     * reducing database overhead. For each entity, the method performs an "upsert" operation, inserting the entity
     * if it does not already exist in the database, or updating it if it does. Upon successful completion,
     * the method returns a list of the IDs of the upserted entities, reflecting their identifiers as stored
     * in the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection
     *                 must be non-null and contain valid data for insertion or update in the database.
     * @return a list of IDs corresponding to the upserted entities. The order of IDs in the returned list
     *         is not guaranteed to match the order of the input collection.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
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
                    case UpsertKey ignore -> result.addAll(upsertAndFetchIds(partition.chunk(), upsertQuery.get(),
                            entityCache.orElse(null)));
                    case UpdateKey u -> result.addAll(updateAndFetchIds(partition.chunk(),
                            updateQueries.computeIfAbsent(u.fields(), ignore -> prepareUpdateQuery(u.fields())),
                            entityCache.orElse(null)));
                }
            });
            return result;
        } finally {
            closeQuietly(Stream.of(upsertQuery.value().stream(), updateQueries.values().stream()).flatMap(identity()));
        }
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of the upserted
     * entities.
     *
     * <p>This method processes the provided entities in batches, optimizing performance for larger collections
     * by reducing database overhead. For each entity, it performs an "upsert" operation, inserting the entity if it
     * does not already exist in the database, or updating it if it does. Upon successful completion, it returns
     * the entities as they exist in the database after the operation. This ensures that the returned entities reflect
     * any changes applied during the upsert process, such as generated primary keys, updated timestamps, or default
     * values set by the database.</p>
     *
     * @param entities an iterable collection of entities to be inserted or updated. Each entity in the collection
     *                 must be non-null and contain valid data for insertion or update in the database.
     * @return a list of upserted entities reflecting their current state in the database. The order of entities
     *         in the returned list is not guaranteed to match the order of the input collection.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity problems,
     *                              constraints violations, or invalid entity data.
     */
    @Override
    public List<E> upsertAndFetch(@Nonnull Iterable<E> entities) {
        return findAllById(upsertAndFetchIds(entities));
    }

    /**
     * Inserts or updates a stream of entities in the database in batches.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted into the database if it does not already exist; if it does exist, it will
     * be updated to reflect the current state of the entity. Batch processing optimizes the performance of the
     * upsert operation for larger data sets by reducing database overhead.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
     */
    @Override
    public void upsert(@Nonnull Stream<E> entities) {
        upsert(entities, defaultBatchSize);
    }

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes.
     *
     * <p>This method processes the provided stream of entities in batches, performing an "upsert" operation on each.
     * For each entity, it will be inserted if it does not already exist in the database, or updated if it does.
     * The batch size can be configured to control the number of entities processed in each database operation,
     * allowing for optimized performance and memory management based on system requirements.</p>
     *
     * @param entities a stream of entities to be inserted or updated. Each entity in the stream must be non-null
     *                 and contain valid data for insertion or update in the database.
     * @param batchSize the number of entities to process in each batch. A larger batch size may improve performance
     *                  but increase memory usage, while a smaller batch size may reduce memory usage but increase
     *                  the number of database operations.
     * @throws PersistenceException if the upsert operation fails due to database issues, such as connectivity
     *                              problems, constraints violations, or invalid entity data.
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
                    case UpsertKey ignore -> upsert(partition.chunk(), upsertQuery.get(),
                            entityCache.orElse(null));
                    case UpdateKey u -> update(partition.chunk(),
                            updateQueries.computeIfAbsent(u.fields(), ignore -> prepareUpdateQuery(u.fields())),
                            entityCache.orElse(null));
                }
            });
        } finally {
            closeQuietly(Stream.of(updateQueries.values().stream(), upsertQuery.value().stream())
                    .flatMap(identity()));
        }
    }

    protected boolean isUpdate(@Nonnull E entity) {
        return isAutoGeneratedPrimaryKey() && !model.isDefaultPrimaryKey(entity.id());
    }

    private PreparedQuery prepareUpsertQuery() {
        var bindVars = ormTemplate.createBindVars();
        var versionAware = new AtomicBoolean();
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () ->
                ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0""", model.type(), bindVars, onDuplicateKey(versionAware))))
                        .managed().prepare());
    }

    protected void upsert(@Nonnull List<E> batch, @Nonnull PreparedQuery query, @Nullable EntityCache<E, ID> cache) {
        if (batch.isEmpty()) {
            return;
        }
        batch.stream().map(this::validateUpsert).forEach(query::addBatch);
        if (cache != null) {
            if (batch.stream().anyMatch(e -> model.isDefaultPrimaryKey(e.id()))) {
                // MySQL can update a record with the same unique key so we need to clear the cache
                // as we cannot predict which record is updated.
                cache.clear();
            } else {
                batch.forEach(e -> cache.remove(e.id()));
            }
        }
        int[] result = query.executeBatch();
        if (IntStream.of(result).anyMatch(r -> r != 1 && r != 2)) {
            throw new PersistenceException("Batch upsert failed.");
        }
    }

    protected List<ID> upsertAndFetchIds(@Nonnull List<E> batch, @Nonnull PreparedQuery query, @Nullable EntityCache<E, ID> cache) {
        if (batch.isEmpty()) {
            return List.of();
        }
        batch.stream().map(this::validateUpsert).forEach(query::addBatch);
        if (cache != null) {
            if (batch.stream().anyMatch(e -> model.isDefaultPrimaryKey(e.id()))) {
                // MySQL can update a record with the same unique key so we need to clear the cache
                // as we cannot predict which record is updated.
                cache.clear();
            } else {
                batch.forEach(e -> cache.remove(e.id()));
            }
        }
        int[] result = query.executeBatch();
        if (IntStream.of(result).anyMatch(r -> r != 1 && r != 2)) {
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
        throw new PersistenceException("MySQL does not support sequence-based generation.");
    }

    @Override
    public List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.insertAndFetchIds(entities);
        }
        throw new PersistenceException("MySQL does not support sequence-based generation.");
    }
}
