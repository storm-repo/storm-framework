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
package st.orm.spi.mariadb;

import jakarta.annotation.Nonnull;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.Entity;
import st.orm.core.template.PreparedQuery;
import st.orm.core.template.Query;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.LazySupplier;
import st.orm.spi.mysql.MySQLEntityRepositoryImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static st.orm.GenerationStrategy.SEQUENCE;
import static st.orm.core.template.SqlInterceptor.intercept;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.impl.StringTemplates.flatten;

/**
 * Implementation of {@link EntityRepository} for MariaDB.
 */
public class MariaDBEntityRepositoryImpl<E extends Record & Entity<ID>, ID>
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
        var versionAware = new AtomicBoolean();
        assert primaryKeyColumns.size() == 1;
        var primaryKeyColumn = primaryKeyColumns.getFirst();
        String pkName = primaryKeyColumn.qualifiedName(ormTemplate.dialect());
        return intercept(sql -> sql.versionAware(versionAware.getPlain()), () -> {
            var query = ormTemplate.query(flatten(raw("""
                INSERT INTO \0
                VALUES \0\0
                RETURNING %s""".formatted(pkName), model.type(), entity, onDuplicateKey(versionAware))));
            return query.getSingleResult(model.primaryKeyType());
        });
    }

    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        if (generationStrategy != SEQUENCE) {
            return super.upsertAndFetchIds(entities);
        }
        LazySupplier<PreparedQuery> updateQuery = new LazySupplier<>(this::prepareUpdateQuery);
        try {
            return chunked(toStream(entities), defaultBatchSize, batch -> {
                var result = new ArrayList<ID>();
                var partition = partition(batch);
                result.addAll(updateAndFetchIds(partition.get(true), updateQuery));
                result.addAll(getUpsertQuery(partition.get(false)).getResultList(model.primaryKeyType()));
                return result.stream();
            }).toList();
        } finally {
            updateQuery.value().ifPresent(PreparedQuery::close);
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
                    RETURNING %s""".formatted(pkName), model.type(), entities, onDuplicateKey(versionAware)))
                ));
    }
}
