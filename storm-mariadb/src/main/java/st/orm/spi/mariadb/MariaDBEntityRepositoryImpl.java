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

import jakarta.annotation.Nonnull;
import st.orm.Metamodel;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.Entity;
import st.orm.core.template.PreparedQuery;
import st.orm.core.template.Query;
import st.orm.core.template.TemplateString;
import st.orm.spi.mysql.MySQLEntityRepositoryImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.repository.impl.DirtySupport.getMaxShapes;
import static st.orm.core.repository.impl.StreamSupport.partitioned;
import static st.orm.core.template.SqlInterceptor.intercept;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.impl.StringTemplates.flatten;

/**
 * Implementation of {@link EntityRepository} for MariaDB.
 */
public class MariaDBEntityRepositoryImpl<E extends Entity<ID>, ID>
        extends MySQLEntityRepositoryImpl<E, ID> {

    public MariaDBEntityRepositoryImpl(@Nonnull ORMTemplate ormTemplate, @Nonnull Model<E, ID> model) {
        super(ormTemplate, model);
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
                RETURNING %s""".formatted(pkName), model.type(), entity)).managed().prepare()) {
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
            RETURNING %s""".formatted(pkName), model.type(), entities))
                .managed();
        return query.getResultList(model.primaryKeyType());
    }

    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        if (generationStrategy != SEQUENCE) {
            return super.upsertAndFetchId(entity);
        }
        if (isUpdate(entity)) {
            update(entity);
            return entity.id();
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
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.upsertAndFetchIds(entities);
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
                    case UpsertKey ignore -> {
                        entityCache.ifPresent(cache -> {
                            if (partition.chunk().stream().anyMatch(e -> model.isDefaultPrimaryKey(e.id()))) {
                                // MySQL/MariaDB can update a record with the same unique key so we need to clear the
                                // cache as we cannot predict which record is updated.
                                cache.clear();
                            } else {
                                partition.chunk().forEach(e -> cache.remove(e.id()));
                            }
                        });
                        result.addAll(getUpsertQuery(partition.chunk()).getResultList(model.primaryKeyType()));
                    }
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
                    RETURNING %s""".formatted(pkName), model.type(), entities, onDuplicateKey(versionAware))))
                        .managed());
    }
}
