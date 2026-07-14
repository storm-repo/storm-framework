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
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import st.orm.Entity;
import st.orm.StormConfig;
import st.orm.core.spi.CacheRetention;
import st.orm.core.spi.EntityCache;
import st.orm.core.spi.TransactionContext;
import st.orm.core.spi.WeakInterner;
import st.orm.core.template.SqlTemplateException;

/**
 * Skips JDBC column decoding for entity column regions whose entity is already cached.
 *
 * <p>Queries that return duplicate entity references (joins that repeat the same parent entity across rows) pay
 * for decoding the parent's columns on every row, even though the mapper's early cache lookup skips construction
 * on a hit. This class probes the same caches <em>before</em> column extraction: for each entity region it decodes
 * only the columns up to and including the primary key, and on a cache hit leaves the remaining columns undecoded.
 * The mapper's early lookup then resolves the entity from the primary key alone and never reads the skipped
 * slots.</p>
 *
 * <p>Columns are always accessed in ascending order, so drivers that only support forward column access within a
 * row are unaffected. Entities found in the query-scoped {@link WeakInterner} are pinned in a strong reference
 * list until the next row is read, guaranteeing that the mapper's subsequent lookup of the same key succeeds even
 * if a garbage collection runs in between (the interner holds entities weakly).</p>
 *
 * <p>The cache policy mirrors the mapper's early lookup exactly: nested entity regions consult the
 * transaction-scoped {@link EntityCache} when the isolation level is {@code REPEATABLE_READ} or higher and the
 * query-scoped {@link WeakInterner} otherwise; the top-level region only consults the {@link EntityCache}, since
 * top-level records are never interned.</p>
 *
 * <p>This class is not thread-safe. A new instance is expected to be created for each query execution.</p>
 */
final class ColumnSkipper {

    /**
     * A flat column region occupied by an entity that is eligible for the early cache lookup.
     *
     * @param start the absolute offset of the region's first column.
     * @param end the absolute offset just past the region's last column.
     * @param pkOffset the absolute offset of the first primary key column.
     * @param pkColumnCount the number of columns the primary key spans.
     * @param pkConstructor the constructor for composite primary keys (null for single-column keys).
     * @param entityType the entity type occupying the region.
     * @param topLevel whether the region covers the top-level record rather than a nested entity.
     */
    record SkipRegion(int start,
                      int end,
                      int pkOffset,
                      int pkColumnCount,
                      @Nullable Constructor<?> pkConstructor,
                      @Nonnull Class<? extends Entity<?>> entityType,
                      boolean topLevel) {}

    /**
     * Reads and converts a single JDBC column value.
     */
    @FunctionalInterface
    interface ColumnReader {

        /**
         * Reads the column at the given zero-based index.
         *
         * @param index the zero-based column index.
         * @return the converted column value, or {@code null} if the column is SQL NULL.
         * @throws SQLException if a database access error occurs.
         */
        @Nullable
        Object read(int index) throws SQLException;
    }

    private final List<SkipRegion> regions;
    private final WeakInterner interner;
    @Nullable
    private final TransactionContext context;
    @Nullable
    private final EntityCache<Entity<?>, ?> topLevelCache;
    private final boolean topLevelCacheReadEnabled;

    /** Strong references to interner hits for the current row; prevents collection before the mapper's lookup. */
    private final List<Entity<?>> pinned = new ArrayList<>();

    /**
     * Creates a new column skipper.
     *
     * @param regions the entity regions eligible for skipping, ordered by start offset; outer regions precede the
     *                regions they contain.
     * @param interner the query-scoped interner, shared with the mapper.
     * @param context the transaction context, or null if not in a transaction.
     * @param topLevelCache the entity cache for the top-level record, or null if not applicable.
     * @param topLevelCacheReadEnabled whether cache reads are enabled for the top-level record.
     */
    ColumnSkipper(@Nonnull List<SkipRegion> regions,
                  @Nonnull WeakInterner interner,
                  @Nullable TransactionContext context,
                  @Nullable EntityCache<Entity<?>, ?> topLevelCache,
                  boolean topLevelCacheReadEnabled) {
        this.regions = regions;
        this.interner = interner;
        this.context = context;
        this.topLevelCache = topLevelCache;
        this.topLevelCacheReadEnabled = topLevelCacheReadEnabled;
    }

    /**
     * Reads a single row into {@code args}, skipping the non-key columns of entity regions that hit the cache.
     * Skipped slots are left {@code null}; the mapper never reads them, as its early lookup returns the cached
     * instance from the primary key alone.
     *
     * @param args the flat argument array to fill; its length must match the query's column count.
     * @param reader reads and converts a single column value.
     * @throws SQLException if a database access error occurs.
     * @throws SqlTemplateException if a composite primary key cannot be constructed.
     */
    void readRow(@Nonnull Object[] args, @Nonnull ColumnReader reader) throws SQLException, SqlTemplateException {
        pinned.clear();
        boolean cacheReadEnabled = context != null && context.isRepeatableRead();
        int i = 0;
        for (SkipRegion region : regions) {
            if (region.start() < i) {
                // The region lies within an outer region that was already skipped.
                continue;
            }
            for (; i < region.start(); i++) {
                args[i] = reader.read(i);
            }
            // Decode up to and including the primary key columns; column access stays in ascending order.
            int pkEnd = region.pkOffset() + region.pkColumnCount();
            for (; i < pkEnd; i++) {
                args[i] = reader.read(i);
            }
            Object pk = extractPk(args, region);
            if (pk != null && isCached(region, pk, cacheReadEnabled)) {
                i = region.end();
            }
        }
        for (; i < args.length; i++) {
            args[i] = reader.read(i);
        }
    }

    /**
     * Extracts the primary key for the given region from the decoded columns.
     *
     * @param args the flat argument array with the region's primary key columns decoded.
     * @param region the region to extract the primary key for.
     * @return the primary key value, or {@code null} if any key column is null or the key cannot be constructed.
     */
    @Nullable
    private Object extractPk(@Nonnull Object[] args, @Nonnull SkipRegion region) throws SqlTemplateException {
        if (region.pkColumnCount() == 1) {
            return args[region.pkOffset()];
        }
        if (region.pkConstructor() == null) {
            return null;
        }
        Object[] pkArgs = new Object[region.pkColumnCount()];
        for (int i = 0; i < region.pkColumnCount(); i++) {
            Object arg = args[region.pkOffset() + i];
            if (arg == null) {
                return null;  // Null in composite PK means no valid PK.
            }
            pkArgs[i] = arg;
        }
        return ObjectMapperFactory.construct(region.pkConstructor(), pkArgs, region.pkOffset());
    }

    /**
     * Checks whether the entity for the given region and primary key is cached, mirroring the cache policy of the
     * mapper's early lookup.
     *
     * @param region the region being probed.
     * @param pk the primary key value.
     * @param cacheReadEnabled whether transaction-scoped cache reads are enabled for this row.
     * @return {@code true} if the entity is cached and decoding the region's remaining columns can be skipped.
     */
    private boolean isCached(@Nonnull SkipRegion region, @Nonnull Object pk, boolean cacheReadEnabled) {
        if (region.topLevel()) {
            // Top-level records are never interned; only the entity cache applies.
            if (topLevelCache == null || !topLevelCacheReadEnabled) {
                return false;
            }
            //noinspection unchecked,rawtypes
            return ((EntityCache) topLevelCache).get(pk).isPresent();
        }
        if (cacheReadEnabled) {
            //noinspection unchecked
            var entityCache = (EntityCache<Entity<?>, ?>) context.entityCache(
                    region.entityType(), CacheRetention.fromConfig(StormConfig.defaults()));
            //noinspection unchecked,rawtypes
            return ((EntityCache) entityCache).get(pk).isPresent();
        }
        Entity<?> cached = interner.get(region.entityType(), pk);
        if (cached != null) {
            pinned.add(cached);
            return true;
        }
        return false;
    }
}
