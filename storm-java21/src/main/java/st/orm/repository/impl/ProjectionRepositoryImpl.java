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
import st.orm.repository.ProjectionRepository;
import st.orm.Projection;
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
public final class ProjectionRepositoryImpl<P extends Record & Projection<ID>, ID> implements ProjectionRepository<P, ID> {
    private final st.orm.core.repository.ProjectionRepository<P, ID> projectionRepository;

    public ProjectionRepositoryImpl(@Nonnull st.orm.core.repository.ProjectionRepository<P, ID> projectionRepository) {
        this.projectionRepository = requireNonNull(projectionRepository);
    }

    @Override
    public Model<P, ID> model() {
        return new ModelImpl<>((st.orm.core.template.impl.ModelImpl<P, ID>) projectionRepository.model());
    }

    @Override
    public Ref<P> ref(@Nonnull ID id) {
        return projectionRepository.ref(id);
    }

    @Override
    public Ref<P> ref(@Nonnull P projection, @Nonnull ID id) {
        return projectionRepository.ref(projection, id);
    }

    @Override
    public QueryBuilder<P, P, ID> select() {
        return new QueryBuilderImpl<>(projectionRepository.select());
    }

    @Override
    public QueryBuilder<P, Long, ID> selectCount() {
        return new QueryBuilderImpl<>(projectionRepository.selectCount());
    }

    @Override
    public <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType) {
        return new QueryBuilderImpl<>(projectionRepository.select(selectType));
    }

    @Override
    public QueryBuilder<P, Ref<P>, ID> selectRef() {
        return new QueryBuilderImpl<>(projectionRepository.selectRef());
    }

    @Override
    public <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(projectionRepository.select(selectType, convert(template)));
    }

    @Override
    public <R extends Record> QueryBuilder<P, Ref<R>, ID> selectRef(@Nonnull Class<R> refType) {
        return new QueryBuilderImpl<>(projectionRepository.selectRef(refType));
    }

    @Override
    public long count() {
        return projectionRepository.count();
    }

    @Override
    public boolean existsById(@Nonnull ID id) {
        return projectionRepository.existsById(id);
    }

    @Override
    public boolean existsByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.existsByRef(ref);
    }

    @Override
    public Optional<P> findById(@Nonnull ID id) {
        return projectionRepository.findById(id);
    }

    @Override
    public Optional<P> findByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.findByRef(ref);
    }

    @Override
    public P getById(@Nonnull ID id) {
        return projectionRepository.getById(id);
    }

    @Override
    public P getByRef(@Nonnull Ref<P> ref) {
        return projectionRepository.getByRef(ref);
    }

    @Override
    public List<P> findAll() {
        return projectionRepository.findAll();
    }

    @Override
    public List<P> findAllById(@Nonnull Iterable<ID> ids) {
        return projectionRepository.findAllById(ids);
    }

    @Override
    public List<P> findAllByRef(@Nonnull Iterable<Ref<P>> refs) {
        return projectionRepository.findAllByRef(refs);
    }

    @Override
    public Stream<P> selectAll() {
        return projectionRepository.selectAll();
    }

    @Override
    public Stream<P> selectById(@Nonnull Stream<ID> ids) {
        return projectionRepository.selectById(ids);
    }

    @Override
    public Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs) {
        return projectionRepository.selectByRef(refs);
    }

    @Override
    public Stream<P> selectById(@Nonnull Stream<ID> ids, int batchSize) {
        return projectionRepository.selectById(ids, batchSize);
    }

    @Override
    public Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs, int batchSize) {
        return projectionRepository.selectByRef(refs, batchSize);
    }

    @Override
    public long countById(@Nonnull Stream<ID> ids) {
        return projectionRepository.countById(ids);
    }

    @Override
    public long countById(@Nonnull Stream<ID> ids, int batchSize) {
        return projectionRepository.countById(ids, batchSize);
    }

    @Override
    public long countByRef(@Nonnull Stream<Ref<P>> refs) {
        return projectionRepository.countByRef(refs);
    }

    @Override
    public long countByRef(@Nonnull Stream<Ref<P>> refs, int batchSize) {
        return projectionRepository.countByRef(refs, batchSize);
    }

    @Override
    public ORMTemplate orm() {
        return new ORMTemplateImpl(projectionRepository.orm());
    }
}
