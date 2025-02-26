/*
 * Copyright 2024 the original author or authors.
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
import jakarta.annotation.Nullable;
import st.orm.BatchCallback;
import st.orm.NoResultException;
import st.orm.NonUniqueResultException;
import st.orm.PersistenceException;
import st.orm.PreparedQuery;
import st.orm.repository.Entity;
import st.orm.spi.EntityRepositoryImpl;
import st.orm.template.Column;
import st.orm.template.Model;
import st.orm.template.ORMTemplate;
import st.orm.template.impl.LazySupplier;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static java.lang.StringTemplate.combine;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Stream.empty;
import static st.orm.Templates.table;
import static st.orm.template.QueryBuilder.slice;
import static st.orm.template.SqlInterceptor.intercept;
import static st.orm.template.impl.StringTemplates.flatten;

/**
 * Implementation of {@link st.orm.repository.EntityRepository} for PostgreSQL.
 */
public class PostgreSQLEntityRepositoryImpl<E extends Record & Entity<ID>, ID>
        extends EntityRepositoryImpl<E, ID> {

    public PostgreSQLEntityRepositoryImpl(@Nonnull ORMTemplate ormTemplate, @Nonnull Model<E, ID> model) {
        super(ormTemplate, model);
    }

    private StringTemplate getVersionString(@Nonnull Class<? extends Record> type, @Nonnull Column column) {
        StringTemplate columnName = StringTemplate.of(column.columnName());
        StringTemplate updateExpression = switch (column.type()) {
            case Class<?> c when Integer.TYPE.isAssignableFrom(c)
                    || Long.TYPE.isAssignableFrom(c)
                    || Integer.class.isAssignableFrom(c)
                    || Long.class.isAssignableFrom(c)
                    || BigInteger.class.isAssignableFrom(c) -> RAW."\{table(type)}.\{columnName} + 1";
            case Class<?> c when Instant.class.isAssignableFrom(c)
                    || Date.class.isAssignableFrom(c)
                    || Calendar.class.isAssignableFrom(c)
                    || Timestamp.class.isAssignableFrom(c) -> RAW."CURRENT_TIMESTAMP";
            default ->
                    throw new PersistenceException(STR."Unsupported version type: \{column.type().getSimpleName()}.");
        };
        return flatten(RAW."\{columnName} = \{updateExpression}");
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
     * @return the conflict clause as a StringTemplate.
     */
    private StringTemplate onConflictClause(@Nonnull AtomicBoolean versionAware) {
        // Determine the conflict target from primary key columns.
        String conflictTarget = model.columns().stream()
                .filter(Column::primaryKey)
                .map(Column::columnName)
                .reduce((a, b) -> STR."\{a}, \{b}")
                .orElseThrow(() -> new PersistenceException("No primary key defined."));
        // Build the assignment list for non-primary key updatable columns.
        var assignments = model.columns().stream()
                .filter(not(Column::primaryKey))
                .filter(Column::updatable)
                .map(column -> {
                    if (column.version()) {
                        versionAware.setPlain(true);
                        return getVersionString(model.type(), column);
                    }
                    return StringTemplate.of(STR."\{column.columnName()} = EXCLUDED.\{column.columnName()}");
                })
                .reduce((left, right) -> combine(left, RAW.", ", right))
                .map(st -> StringTemplate.combine(RAW."DO UPDATE SET ", st))
                .orElse(StringTemplate.of("DO NOTHING"));
        return flatten(combine(RAW."\nON CONFLICT (", StringTemplate.of(conflictTarget), RAW.") \{assignments}"));
    }

    protected E validateUpsert(@Nonnull E entity) {
        if (!autoGeneratedPrimaryKey && model.isDefaultPrimaryKey(entity.id())) {
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
        try (var _ = intercept(sql -> sql.versionAware(versionAware.getPlain()))) {
            var query = ormTemplate.query(flatten(RAW."""
                    INSERT INTO \{model.type()}
                    VALUES \{entity}\{onConflictClause(versionAware)}"""));
            query.executeUpdate();
        }
    }

    /**
     * Inserts or updates a single entity in the database and returns its ID.
     */
    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        if (isUpdate(entity)) {
            update(entity);
            return entity.id();
        }
        validateUpsert(entity);
        var versionAware = new AtomicBoolean();
        try (var _ = intercept(sql -> sql.versionAware(versionAware.getPlain()));
             var query = ormTemplate.query(flatten(RAW."""
                    INSERT INTO \{model.type()}
                    VALUES \{entity}\{onConflictClause(versionAware)}""")).prepare()) {
            query.executeUpdate();
            if (autoGeneratedPrimaryKey) {
                try (var stream = query.getGeneratedKeys(model.primaryKeyType())) {
                    return stream.reduce((_, __) -> {
                        throw new NonUniqueResultException("Expected single result, but found more than one.");
                    }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
                }
            }
            return entity.id();
        }
    }

    /**
     * Inserts or updates a single entity in the database and returns the entity with its current state.
     */
    @Override
    public E upsertAndFetch(@Nonnull E entity) {
        return select(upsertAndFetchId(entity));
    }

    /**
     * Inserts or updates a collection of entities in the database in batches.
     */
    @Override
    public void upsert(@Nonnull Iterable<E> entities) {
        upsert(toStream(entities), defaultBatchSize);
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of their IDs.
     */
    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        LazySupplier<PreparedQuery> updateQuery = new LazySupplier<>(this::prepareUpdateQuery);
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            return slice(toStream(entities), defaultBatchSize, batch -> {
                var result = new ArrayList<ID>();
                var partition = partition(batch);
                updateAndFetchIds(partition.get(true), updateQuery, ids -> result.addAll(ids.toList()));
                upsertAndFetchIds(partition.get(false), upsertQuery, ids -> result.addAll(ids.toList()));
                return result.stream();
            }).toList();
        } finally {
            closeQuietly(updateQuery, upsertQuery);
        }
    }

    /**
     * Inserts or updates a collection of entities in the database in batches and returns a list of the upserted entities.
     */
    @Override
    public List<E> upsertAndFetch(@Nonnull Iterable<E> entities) {
        return select(upsertAndFetchIds(entities));
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
        LazySupplier<PreparedQuery> updateQuery = new LazySupplier<>(this::prepareUpdateQuery);
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            slice(entities, batchSize).forEach(batch -> {
                var partition = partition(batch);
                updateAndFetch(partition.get(true), updateQuery, null);
                upsertAndFetch(partition.get(false), upsertQuery, null);
            });
        } finally {
            closeQuietly(updateQuery, upsertQuery);
        }
    }

    /**
     * Inserts or updates a stream of entities in the database in batches and retrieves their IDs through a callback.
     */
    @Override
    public void upsertAndFetchIds(@Nonnull Stream<E> entities, @Nonnull BatchCallback<ID> callback) {
        upsertAndFetchIds(entities, defaultBatchSize, callback);
    }

    /**
     * Inserts or updates a stream of entities in the database in batches and retrieves the updated entities through a callback.
     */
    @Override
    public void upsertAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback) {
        upsertAndFetch(entities, defaultBatchSize, callback);
    }

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes and retrieves their IDs through a callback.
     */
    @Override
    public void upsertAndFetchIds(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<ID> callback) {
        LazySupplier<PreparedQuery> updateQuery = new LazySupplier<>(this::prepareUpdateQuery);
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            slice(entities, batchSize).forEach(batch -> {
                var partition = partition(batch);
                updateAndFetchIds(partition.get(true), updateQuery, callback);
                upsertAndFetchIds(partition.get(false), upsertQuery, callback);
            });
        } finally {
            closeQuietly(updateQuery, upsertQuery);
        }
    }

    /**
     * Inserts or updates a stream of entities in the database in configurable batch sizes and retrieves the updated entities through a callback.
     */
    @Override
    public void upsertAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback) {
        LazySupplier<PreparedQuery> updateQuery = new LazySupplier<>(this::prepareUpdateQuery);
        LazySupplier<PreparedQuery> upsertQuery = new LazySupplier<>(this::prepareUpsertQuery);
        try {
            slice(entities, batchSize).forEach(batch -> {
                var partition = partition(batch);
                updateAndFetch(partition.get(true), updateQuery, callback);
                upsertAndFetch(partition.get(false), upsertQuery, callback);
            });
        } finally {
            closeQuietly(updateQuery, upsertQuery);
        }
    }

    private boolean isUpdate(@Nonnull E entity) {
        return autoGeneratedPrimaryKey && !model.isDefaultPrimaryKey(entity.id());
    }

    private Map<Boolean, List<E>> partition(@Nonnull List<E> entities) {
        return entities.stream().collect(partitioningBy(this::isUpdate));
    }

    protected PreparedQuery prepareUpsertQuery() {
        var bindVars = ormTemplate.createBindVars();
        var versionAware = new AtomicBoolean();
        try (var _ = intercept(sql -> sql.versionAware(versionAware.getPlain()))) {
            return ormTemplate.query(flatten(RAW."""
                    INSERT INTO \{model.type()}
                    VALUES \{bindVars}\{onConflictClause(versionAware)}""")).prepare();
        }
    }

    protected void upsertAndFetchIds(@Nonnull List<E> batch, @Nonnull Supplier<PreparedQuery> querySupplier, @Nullable BatchCallback<ID> callback) {
        if (batch.isEmpty()) {
            if (callback != null) {
                callback.process(empty());
            }
            return;
        }
        var query = querySupplier.get();
        batch.stream().map(this::validateUpsert).map(Record.class::cast).forEach(query::addBatch);
        int[] result = query.executeBatch();
        if (IntStream.of(result).anyMatch(r -> r != 0 && r != 1 && r != 2)) {
            throw new PersistenceException("Batch upsert failed.");
        }
        if (callback != null) {
            if (autoGeneratedPrimaryKey) {
                try (var generatedKeys = query.getGeneratedKeys(model.primaryKeyType())) {
                    callback.process(generatedKeys);
                }
            } else {
                callback.process(batch.stream().map(Entity::id));
            }
        }
    }

    protected void upsertAndFetch(@Nonnull List<E> batch, @Nonnull Supplier<PreparedQuery> querySupplier, @Nullable BatchCallback<E> callback) {
        upsertAndFetchIds(batch, querySupplier, callback == null ? null : ids -> {
            try (var stream = select(ids)) {
                callback.process(stream);
            }
        });
    }

    /**
     * Helper to close lazy-supplied queries without exceptions interrupting each other.
     */
    private void closeQuietly(LazySupplier<PreparedQuery> updateQuery, LazySupplier<PreparedQuery> upsertQuery) {
        try {
            upsertQuery.value().ifPresent(PreparedQuery::close);
        } finally {
            updateQuery.value().ifPresent(PreparedQuery::close);
        }
    }
}