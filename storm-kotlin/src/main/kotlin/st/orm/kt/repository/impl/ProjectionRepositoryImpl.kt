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
package st.orm.kt.repository.impl

import st.orm.Projection
import st.orm.Ref
import st.orm.kt.repository.ProjectionRepository
import st.orm.kt.template.*
import st.orm.kt.template.impl.ModelImpl
import st.orm.kt.template.impl.ORMTemplateImpl
import st.orm.kt.template.impl.QueryBuilderImpl
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 */
class ProjectionRepositoryImpl<P, ID : Any>(
    private val core: st.orm.core.repository.ProjectionRepository<P, ID>
) : ProjectionRepository<P, ID> where P : Record, P : Projection<ID> {

    override val orm: ORMTemplate
        get() = ORMTemplateImpl(core.orm())

    override val model: Model<P, ID>
        get() = ModelImpl<P, ID>(core.model())

    override fun ref(id: ID): Ref<P> {
        return core.ref(id)
    }

    override fun ref(projection: P, id: ID): Ref<P> {
        return core.ref(projection, id)
    }

    override fun select(): QueryBuilder<P, P, ID> {
        return QueryBuilderImpl(core.select())
    }

    override fun selectCount(): QueryBuilder<P, Long, ID> {
        return QueryBuilderImpl(core.selectCount())
    }

    override fun <R : Any> select(selectType: KClass<R>): QueryBuilder<P, R, ID> {
        return QueryBuilderImpl(core.select(selectType.java))
    }

    override fun selectRef(): QueryBuilder<P, Ref<P>, ID> {
        return QueryBuilderImpl<P, Ref<P>, ID>(core.selectRef())
    }

    override fun <R : Any> select(
        selectType: KClass<R>,
        template: TemplateString
    ): QueryBuilder<P, R, ID> {
        return QueryBuilderImpl<P, R, ID>(
            core.select(selectType.java, template.unwrap)
        )
    }

    override fun <R : Record> selectRef(refType: KClass<R>): QueryBuilder<P, Ref<R>, ID> {
        return QueryBuilderImpl(core.selectRef(refType.java))
    }

    override fun count(): Long {
        return core.count()
    }

    override fun exists(): Boolean {
        return core.exists()
    }

    override fun existsById(id: ID): Boolean {
        return core.existsById(id)
    }

    override fun existsByRef(ref: Ref<P>): Boolean {
        return core.existsByRef(ref)
    }

    override fun findById(id: ID): P? {
        return core.findById(id).orElse(null)
    }

    override fun findByRef(ref: Ref<P>): P? {
        return core.findByRef(ref).orElse(null)
    }

    override fun getById(id: ID): P {
        return core.getById(id)
    }

    override fun getByRef(ref: Ref<P>): P {
        return core.getByRef(ref)
    }

    override fun findAll(): List<P> {
        return core.findAll()
    }

    override fun findAllById(ids: Iterable<ID>): List<P> {
        return core.findAllById(ids)
    }

    override fun findAllByRef(refs: Iterable<Ref<P>>): List<P> {
        return core.findAllByRef(refs)
    }

    override fun selectAll(): Stream<P> {
        return core.selectAll()
    }

    override fun selectById(ids: Stream<ID>): Stream<P> {
        return core.selectById(ids)
    }

    override fun selectByRef(refs: Stream<Ref<P>>): Stream<P> {
        return core.selectByRef(refs)
    }

    override fun selectById(ids: Stream<ID>, batchSize: Int): Stream<P> {
        return core.selectById(ids, batchSize)
    }

    override fun selectByRef(refs: Stream<Ref<P>>, batchSize: Int): Stream<P> {
        return core.selectByRef(refs, batchSize)
    }

    override fun countById(ids: Stream<ID>): Long {
        return core.countById(ids)
    }

    override fun countById(ids: Stream<ID>, batchSize: Int): Long {
        return core.countById(ids, batchSize)
    }

    override fun countByRef(refs: Stream<Ref<P>>): Long {
        return core.countByRef(refs)
    }

    override fun countByRef(refs: Stream<Ref<P>>, batchSize: Int): Long {
        return core.countByRef(refs, batchSize)
    }
}
