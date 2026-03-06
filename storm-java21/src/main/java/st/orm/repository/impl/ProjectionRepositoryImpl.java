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
package st.orm.repository.impl;

import static java.util.Objects.requireNonNull;
import static st.orm.template.impl.StringTemplates.convert;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import st.orm.Data;
import st.orm.Metamodel;
import st.orm.Page;
import st.orm.Pageable;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.Slice;
import st.orm.repository.ProjectionRepository;
import st.orm.template.Model;
import st.orm.template.ORMTemplate;
import st.orm.template.QueryBuilder;
import st.orm.template.impl.ModelImpl;
import st.orm.template.impl.ORMTemplateImpl;
import st.orm.template.impl.QueryBuilderImpl;

/**
 */
public final class ProjectionRepositoryImpl<P extends Projection<ID>, ID> implements ProjectionRepository<P, ID> {
    private final st.orm.core.repository.ProjectionRepository<P, ID> core;

    public ProjectionRepositoryImpl(@Nonnull st.orm.core.repository.ProjectionRepository<P, ID> core) {
        this.core = requireNonNull(core);
    }

    @Override
    public Model<P, ID> model() {
        return new ModelImpl<>((st.orm.core.template.impl.ModelImpl<P, ID>) core.model());
    }

    @Override
    public Ref<P> ref(@Nonnull ID id) {
        return core.ref(id);
    }

    @Override
    public Ref<P> ref(@Nonnull P projection, @Nonnull ID id) {
        return core.ref(projection, id);
    }

    @Override
    public QueryBuilder<P, P, ID> select() {
        return new QueryBuilderImpl<>(core.select());
    }

    @Override
    public QueryBuilder<P, Long, ID> selectCount() {
        return new QueryBuilderImpl<>(core.selectCount());
    }

    @Override
    public <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType) {
        return new QueryBuilderImpl<>(core.select(selectType));
    }

    @Override
    public QueryBuilder<P, Ref<P>, ID> selectRef() {
        return new QueryBuilderImpl<>(core.selectRef());
    }

    @Override
    public <R> QueryBuilder<P, R, ID> select(@Nonnull Class<R> selectType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(core.select(selectType, convert(template)));
    }

    @Override
    public <R extends Data> QueryBuilder<P, Ref<R>, ID> selectRef(@Nonnull Class<R> refType) {
        return new QueryBuilderImpl<>(core.selectRef(refType));
    }

    @Override
    public long count() {
        return core.count();
    }

    @Override
    public boolean exists() {
        return core.exists();
    }

    @Override
    public boolean existsById(@Nonnull ID id) {
        return core.existsById(id);
    }

    @Override
    public boolean existsByRef(@Nonnull Ref<P> ref) {
        return core.existsByRef(ref);
    }

    // Page methods.

    @Override
    public Page<P> page(int pageNumber, int pageSize) {
        return core.page(pageNumber, pageSize);
    }

    @Override
    public Page<P> page(@Nonnull Pageable pageable) {
        return core.page(pageable);
    }

    @Override
    public Page<Ref<P>> pageRef(int pageNumber, int pageSize) {
        return core.pageRef(pageNumber, pageSize);
    }

    @Override
    public Page<Ref<P>> pageRef(@Nonnull Pageable pageable) {
        return core.pageRef(pageable);
    }

    // Slice methods.

    @Override
    public <V> Slice<P> slice(@Nonnull Metamodel.Key<P, V> key, int size) {
        return core.slice(key, size);
    }

    @Override
    public <V> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, int size) {
        return core.sliceBefore(key, size);
    }

    @Override
    public <V> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, int size) {
        return core.sliceBeforeRef(key, size);
    }

    @Override
    public <V> Slice<P> sliceAfter(@Nonnull Metamodel.Key<P, V> key, @Nonnull V after, int size) {
        return core.sliceAfter(key, after, size);
    }

    @Override
    public <V> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, @Nonnull V before, int size) {
        return core.sliceBefore(key, before, size);
    }

    @Override
    public <V> Slice<Ref<P>> sliceRef(@Nonnull Metamodel.Key<P, V> key, int size) {
        return core.sliceRef(key, size);
    }

    @Override
    public <V> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull V after, int size) {
        return core.sliceAfterRef(key, after, size);
    }

    @Override
    public <V> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull V before, int size) {
        return core.sliceBeforeRef(key, before, size);
    }

    // Ref cursor slice methods.

    @Override
    public <V extends Data> Slice<P> sliceAfter(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> after, int size) {
        return core.sliceAfter(key, after, size);
    }

    @Override
    public <V extends Data> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> before, int size) {
        return core.sliceBefore(key, before, size);
    }

    @Override
    public <V extends Data> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> after, int size) {
        return core.sliceAfterRef(key, after, size);
    }

    @Override
    public <V extends Data> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> before, int size) {
        return core.sliceBeforeRef(key, before, size);
    }

    // Composite keyset slice methods.

    @Override
    public <V, S> Slice<P> slice(@Nonnull Metamodel.Key<P, V> key, @Nonnull Metamodel<P, S> sort, int size) {
        return core.slice(key, sort, size);
    }

    @Override
    public <V, S> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, @Nonnull Metamodel<P, S> sort, int size) {
        return core.sliceBefore(key, sort, size);
    }

    @Override
    public <V, S> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Metamodel<P, S> sort, int size) {
        return core.sliceBeforeRef(key, sort, size);
    }

    @Override
    public <V, S> Slice<P> sliceAfter(@Nonnull Metamodel.Key<P, V> key, @Nonnull V keyAfter,
                                       @Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter, int size) {
        return core.sliceAfter(key, keyAfter, sort, sortAfter, size);
    }

    @Override
    public <V, S> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, @Nonnull V keyBefore,
                                        @Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore, int size) {
        return core.sliceBefore(key, keyBefore, sort, sortBefore, size);
    }

    @Override
    public <V extends Data, S> Slice<P> sliceAfter(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> keyAfter,
                                                    @Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                                    int size) {
        return core.sliceAfter(key, keyAfter, sort, sortAfter, size);
    }

    @Override
    public <V extends Data, S> Slice<P> sliceBefore(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> keyBefore,
                                                     @Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                                     int size) {
        return core.sliceBefore(key, keyBefore, sort, sortBefore, size);
    }

    @Override
    public <V, S> Slice<Ref<P>> sliceRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Metamodel<P, S> sort, int size) {
        return core.sliceRef(key, sort, size);
    }

    @Override
    public <V, S> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull V keyAfter,
                                               @Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter, int size) {
        return core.sliceAfterRef(key, keyAfter, sort, sortAfter, size);
    }

    @Override
    public <V, S> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull V keyBefore,
                                                @Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore, int size) {
        return core.sliceBeforeRef(key, keyBefore, sort, sortBefore, size);
    }

    @Override
    public <V extends Data, S> Slice<Ref<P>> sliceAfterRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> keyAfter,
                                                            @Nonnull Metamodel<P, S> sort, @Nonnull S sortAfter,
                                                            int size) {
        return core.sliceAfterRef(key, keyAfter, sort, sortAfter, size);
    }

    @Override
    public <V extends Data, S> Slice<Ref<P>> sliceBeforeRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> keyBefore,
                                                             @Nonnull Metamodel<P, S> sort, @Nonnull S sortBefore,
                                                             int size) {
        return core.sliceBeforeRef(key, keyBefore, sort, sortBefore, size);
    }

    @Override
    public Optional<P> findById(@Nonnull ID id) {
        return core.findById(id);
    }

    @Override
    public Optional<P> findByRef(@Nonnull Ref<P> ref) {
        return core.findByRef(ref);
    }

    @Override
    public P getById(@Nonnull ID id) {
        return core.getById(id);
    }

    @Override
    public P getByRef(@Nonnull Ref<P> ref) {
        return core.getByRef(ref);
    }

    @Override
    public <V> Optional<P> findBy(@Nonnull Metamodel.Key<P, V> key, @Nonnull V value) {
        return core.findBy(key, value);
    }

    @Override
    public <V> P getBy(@Nonnull Metamodel.Key<P, V> key, @Nonnull V value) {
        return core.getBy(key, value);
    }

    @Override
    public <V extends Data> Optional<P> findByRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> value) {
        return core.findByRef(key, value);
    }

    @Override
    public <V extends Data> P getByRef(@Nonnull Metamodel.Key<P, V> key, @Nonnull Ref<V> value) {
        return core.getByRef(key, value);
    }

    @Override
    public List<P> findAll() {
        return core.findAll();
    }

    @Override
    public List<P> findAllById(@Nonnull Iterable<ID> ids) {
        return core.findAllById(ids);
    }

    @Override
    public List<P> findAllByRef(@Nonnull Iterable<Ref<P>> refs) {
        return core.findAllByRef(refs);
    }

    @Override
    public Stream<P> selectAll() {
        return core.selectAll();
    }

    @Override
    public Stream<P> selectById(@Nonnull Stream<ID> ids) {
        return core.selectById(ids);
    }

    @Override
    public Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs) {
        return core.selectByRef(refs);
    }

    @Override
    public Stream<P> selectById(@Nonnull Stream<ID> ids, int batchSize) {
        return core.selectById(ids, batchSize);
    }

    @Override
    public Stream<P> selectByRef(@Nonnull Stream<Ref<P>> refs, int batchSize) {
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
    public long countByRef(@Nonnull Stream<Ref<P>> refs) {
        return core.countByRef(refs);
    }

    @Override
    public long countByRef(@Nonnull Stream<Ref<P>> refs, int batchSize) {
        return core.countByRef(refs, batchSize);
    }

    @Override
    public ORMTemplate orm() {
        return new ORMTemplateImpl(core.orm());
    }
}
