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
package st.orm.spi.mariadb;

import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.template.SqlInterceptor.intercept;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.impl.StringTemplates.flatten;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import st.orm.Entity;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Query;
import st.orm.core.template.TemplateString;
import st.orm.spi.mysql.MySQLEntityRepositoryImpl;

/**
 * Implementation of {@link EntityRepository} for MariaDB.
 */
public class MariaDBEntityRepositoryImpl<E extends Entity<ID>, ID>
        extends MySQLEntityRepositoryImpl<E, ID> {

    public MariaDBEntityRepositoryImpl(ORMTemplate ormTemplate, Model<E, ID> model) {
        super(ormTemplate, model);
    }

    @Override
    public ID insertAndFetchId(E entity) {
        if (generationStrategy != SEQUENCE) {
            return super.insertAndFetchId(entity);
        }
        return insertAndFetchIdReturning(entity);
    }

    /**
     * MariaDB supports sequences, but its {@link MySQLEntityRepositoryImpl} parent rejects sequence-based generation.
     * This override keeps the {@code RETURNING} path for SEQUENCE keys and delegates IDENTITY keys to
     * {@code super} (which routes through the core multi-row {@code RETURNING} path).
     */
    @Override
    public List<ID> insertAndFetchIds(Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.insertAndFetchIds(entities);
        }
        List<E> transformed = toStream(entities)
                .map(this::fireBeforeInsert)
                .map(this::validateInsert)
                .toList();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        var query = ormTemplate.query(TemplateString.raw("""
            INSERT INTO \0
            VALUES \0
            RETURNING %s""".formatted(pkName), model.type(), transformed))
                .managed();
        List<ID> ids = query.getResultList(model.primaryKeyType());
        fireAfterInsert(transformed, ids);
        return ids;
    }

    @Override
    protected ID doUpsertAndFetchId(E entity) {
        if (generationStrategy != SEQUENCE) {
            return super.doUpsertAndFetchId(entity);
        }
        validateUpsert(entity);
        entityCache().ifPresent(cache -> {
            if (model.isDefaultPrimaryKey(entity.id())) {
                // MySQL/MariaDB can update a record with the same unique key so we need to clear the cache
                // as we cannot predict which record is updated.
                cache.clear();
            } else {
                cache.remove(entity.id());
            }
        });
        var versionAware = new AtomicBoolean();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            var query = ormTemplate.query(flatten(raw("""
                INSERT INTO \0
                VALUES \0\0
                RETURNING %s""".formatted(pkName), model.type(), entity, onDuplicateKey(versionAware))))
                    .managed();
            return query.getSingleResult(model.primaryKeyType());
        });
    }

    @Override
    public List<ID> upsertAndFetchIds(Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.upsertAndFetchIds(entities);
        }
        // SEQUENCE path: use a single query with RETURNING clause instead of batched prepared statements.
        return upsertAndFetchIdsPartitioned(entities, (chunk, entityCache) -> {
            List<E> batch = hasEntityCallbacks()
                    ? chunk.stream().map(this::fireBeforeUpsert).toList()
                    : chunk;
            entityCache.ifPresent(cache -> {
                if (batch.stream().anyMatch(e -> model.isDefaultPrimaryKey(e.id()))) {
                    // MySQL/MariaDB can update a record with the same unique key so we need to clear the
                    // cache as we cannot predict which record is updated.
                    cache.clear();
                } else {
                    batch.forEach(e -> cache.remove(e.id()));
                }
            });
            List<ID> ids = getUpsertQuery(batch).getResultList(model.primaryKeyType());
            if (hasEntityCallbacks()) {
                batch.forEach(this::fireAfterUpsert);
            }
            return ids;
        });
    }

    private Query getUpsertQuery(Iterable<E> entities) {
        var versionAware = new AtomicBoolean();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () ->
                ormTemplate.query(flatten(raw("""
                    INSERT INTO \0
                    VALUES \0\0
                    RETURNING %s""".formatted(pkName), model.type(), entities, onDuplicateKey(versionAware))))
                        .managed());
    }
}
