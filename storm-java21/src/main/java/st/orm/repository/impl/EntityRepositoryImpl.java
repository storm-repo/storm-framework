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
package st.orm.repository.impl;

import jakarta.annotation.Nonnull;
import st.orm.repository.BatchCallback;
import st.orm.repository.EntityRepository;
import st.orm.Entity;
import st.orm.Ref;
import st.orm.template.Model;
import st.orm.template.ORMTemplate;
import st.orm.template.QueryBuilder;
import st.orm.template.impl.ModelImpl;
import st.orm.template.impl.ORMTemplateImpl;
import st.orm.template.impl.QueryBuilderImpl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.template.impl.StringTemplates.convert;

/**
 */
public final class EntityRepositoryImpl<E extends Record & Entity<ID>, ID> implements EntityRepository<E, ID> {
    private final st.orm.core.EntityRepository<E, ID> core;

    public EntityRepositoryImpl(@Nonnull st.orm.core.EntityRepository<E, ID> core) {
        this.core = requireNonNull(core);
    }

    @Override
    public Model<E, ID> model() {
        return new ModelImpl<>((st.orm.core.template.impl.ModelImpl<E, ID>) core.model());
    }

    @Override
    public Ref<E> ref(@Nonnull ID id) {
        return core.ref(id);
    }

    @Override
    public Ref<E> ref(@Nonnull E entity) {
        return core.ref(entity);
    }

    @Override
    public Ref<E> unload(@Nonnull E entity) {
        return core.unload(entity);
    }

    @Override
    public QueryBuilder<E, E, ID> select() {
        return new QueryBuilderImpl<>(core.select());
    }

    @Override
    public QueryBuilder<E, Long, ID> selectCount() {
        return new QueryBuilderImpl<>(core.selectCount());
    }

    @Override
    public <R> QueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType) {
        return new QueryBuilderImpl<>(core.select(selectType));
    }

    @Override
    public QueryBuilder<E, Ref<E>, ID> selectRef() {
        return new QueryBuilderImpl<>(core.selectRef());
    }

    @Override
    public <R> QueryBuilder<E, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(core.select(selectType, convert(template)));
    }

    @Override
    public <R extends Record> QueryBuilder<E, Ref<R>, ID> selectRef(@Nonnull Class<R> refType) {
        return new QueryBuilderImpl<>(core.selectRef(refType));
    }

    @Override
    public QueryBuilder<E, ?, ID> delete() {
        return new QueryBuilderImpl<>(core.delete());
    }

    @Override
    public long count() {
        return core.count();
    }

    @Override
    public boolean existsById(@Nonnull ID id) {
        return core.existsById(id);
    }

    @Override
    public boolean existsByRef(@Nonnull Ref<E> ref) {
        return core.existsByRef(ref);
    }

    @Override
    public void insert(@Nonnull E entity) {
        core.insert(entity);
    }

    @Override
    public void insert(@Nonnull E entity, boolean ignoreAutoGenerate) {
        core.insert(entity, ignoreAutoGenerate);
    }

    @Override
    public ID insertAndFetchId(@Nonnull E entity) {
        return core.insertAndFetchId(entity);
    }

    @Override
    public E insertAndFetch(@Nonnull E entity) {
        return core.insertAndFetch(entity);
    }

    @Override
    public void update(@Nonnull E entity) {
        core.update(entity);
    }

    @Override
    public E updateAndFetch(@Nonnull E entity) {
        return core.updateAndFetch(entity);
    }

    @Override
    public void upsert(@Nonnull E entity) {
        core.upsert(entity);
    }

    @Override
    public ID upsertAndFetchId(@Nonnull E entity) {
        return core.upsertAndFetchId(entity);
    }

    @Override
    public E upsertAndFetch(@Nonnull E entity) {
        return core.upsertAndFetch(entity);
    }

    @Override
    public void delete(@Nonnull E entity) {
        core.delete(entity);
    }

    @Override
    public void deleteById(@Nonnull ID id) {
        core.deleteById(id);
    }

    @Override
    public void deleteByRef(@Nonnull Ref<E> ref) {
        core.deleteByRef(ref);
    }

    @Override
    public void deleteAll() {
        core.deleteAll();
    }

    @Override
    public Optional<E> findById(@Nonnull ID id) {
        return core.findById(id);
    }

    @Override
    public Optional<E> findByRef(@Nonnull Ref<E> ref) {
        return core.findByRef(ref);
    }

    @Override
    public E getById(@Nonnull ID id) {
        return core.getById(id);
    }

    @Override
    public E getByRef(@Nonnull Ref<E> ref) {
        return core.getByRef(ref);
    }

    @Override
    public List<E> findAll() {
        return core.findAll();
    }

    @Override
    public List<E> findAllById(@Nonnull Iterable<ID> ids) {
        return core.findAllById(ids);
    }

    @Override
    public List<E> findAllByRef(@Nonnull Iterable<Ref<E>> refs) {
        return core.findAllByRef(refs);
    }

    @Override
    public void insert(@Nonnull Iterable<E> entities) {
        core.insert(entities);
    }

    @Override
    public void insert(@Nonnull Iterable<E> entities, boolean ignoreAutoGenerate) {
        core.insert(entities, ignoreAutoGenerate);
    }

    @Override
    public List<ID> insertAndFetchIds(@Nonnull Iterable<E> entities) {
        return core.insertAndFetchIds(entities);
    }

    @Override
    public List<E> insertAndFetch(@Nonnull Iterable<E> entities) {
        return core.insertAndFetch(entities);
    }

    @Override
    public void update(@Nonnull Iterable<E> entities) {
        core.update(entities);
    }

    @Override
    public List<E> updateAndFetch(@Nonnull Iterable<E> entities) {
        return core.updateAndFetch(entities);
    }

    @Override
    public void upsert(@Nonnull Iterable<E> entities) {
        core.upsert(entities);
    }

    @Override
    public List<ID> upsertAndFetchIds(@Nonnull Iterable<E> entities) {
        return core.upsertAndFetchIds(entities);
    }

    @Override
    public List<E> upsertAndFetch(@Nonnull Iterable<E> entities) {
        return core.upsertAndFetch(entities);
    }

    @Override
    public void delete(@Nonnull Iterable<E> entities) {
        core.delete(entities);
    }

    @Override
    public void deleteByRef(@Nonnull Iterable<Ref<E>> refs) {
        core.deleteByRef(refs);
    }

    @Override
    public Stream<E> selectAll() {
        return core.selectAll();
    }

    @Override
    public Stream<E> selectById(@Nonnull Stream<ID> ids) {
        return core.selectById(ids);
    }

    @Override
    public Stream<E> selectByRef(@Nonnull Stream<Ref<E>> refs) {
        return core.selectByRef(refs);
    }

    @Override
    public Stream<E> selectById(@Nonnull Stream<ID> ids, int batchSize) {
        return core.selectById(ids, batchSize);
    }

    @Override
    public Stream<E> selectByRef(@Nonnull Stream<Ref<E>> refs, int batchSize) {
        return core.selectByRef(refs, batchSize);
    }

    @Override
    public long countById(@Nonnull Stream<ID> ids) {
        return core.countById(ids);
    }

    @Override
    public long countById(@Nonnull Stream<ID> ids, int batchSize) {
        return core.countById(ids, batchSize);
    }

    @Override
    public long countByRef(@Nonnull Stream<Ref<E>> refs) {
        return core.countByRef(refs);
    }

    @Override
    public long countByRef(@Nonnull Stream<Ref<E>> refs, int batchSize) {
        return core.countByRef(refs, batchSize);
    }

    @Override
    public void insert(@Nonnull Stream<E> entities) {
        core.insert(entities);
    }

    @Override
    public void insert(@Nonnull Stream<E> entities, boolean ignoreAutoGenerate) {
        core.insert(entities, ignoreAutoGenerate);
    }

    @Override
    public void insert(@Nonnull Stream<E> entities, int batchSize) {
        core.insert(entities, batchSize);
    }

    @Override
    public void insert(@Nonnull Stream<E> entities, int batchSize, boolean ignoreAutoGenerate) {
        core.insert(entities, batchSize, ignoreAutoGenerate);
    }

    @Override
    public void insertAndFetchIds(@Nonnull Stream<E> entities, @Nonnull BatchCallback<ID> callback) {
        core.insertAndFetchIds(entities, callback::process);
    }

    @Override
    public void insertAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback) {
        core.insertAndFetch(entities, callback::process);
    }

    @Override
    public void insertAndFetchIds(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<ID> callback) {
        core.insertAndFetchIds(entities, batchSize, callback::process);
    }

    @Override
    public void insertAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback) {
        core.insertAndFetch(entities, batchSize, callback::process);
    }

    @Override
    public void update(@Nonnull Stream<E> entities) {
        core.update(entities);
    }

    @Override
    public void update(@Nonnull Stream<E> entities, int batchSize) {
        core.update(entities, batchSize);
    }

    @Override
    public void updateAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback) {
        core.updateAndFetch(entities, callback::process);
    }

    @Override
    public void updateAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback) {
        core.updateAndFetch(entities, batchSize, callback::process);
    }

    @Override
    public void upsert(@Nonnull Stream<E> entities) {
        core.upsert(entities);
    }

    @Override
    public void upsert(@Nonnull Stream<E> entities, int batchSize) {
        core.upsert(entities, batchSize);
    }

    @Override
    public void upsertAndFetchIds(@Nonnull Stream<E> entities, @Nonnull BatchCallback<ID> callback) {
        core.upsertAndFetchIds(entities, callback::process);
    }

    @Override
    public void upsertAndFetch(@Nonnull Stream<E> entities, @Nonnull BatchCallback<E> callback) {
        core.upsertAndFetch(entities, callback::process);
    }

    @Override
    public void upsertAndFetchIds(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<ID> callback) {
        core.upsertAndFetchIds(entities, batchSize, callback::process);
    }

    @Override
    public void upsertAndFetch(@Nonnull Stream<E> entities, int batchSize, @Nonnull BatchCallback<E> callback) {
        core.upsertAndFetch(entities, batchSize, callback::process);
    }

    @Override
    public void delete(@Nonnull Stream<E> entities) {
        core.delete(entities);
    }

    @Override
    public void delete(@Nonnull Stream<E> entities, int batchSize) {
        core.delete(entities, batchSize);
    }

    @Override
    public void deleteByRef(@Nonnull Stream<Ref<E>> refs) {
        core.deleteByRef(refs);
    }

    @Override
    public void deleteByRef(@Nonnull Stream<Ref<E>> refs, int batchSize) {
        core.deleteByRef(refs, batchSize);
    }

    @Override
    public ORMTemplate orm() {
        return new ORMTemplateImpl(core.orm());
    }
}
