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


import st.orm.Entity
import st.orm.Ref
import st.orm.kt.repository.BatchCallback
import st.orm.kt.repository.EntityRepository
import st.orm.kt.template.*
import st.orm.kt.template.impl.ModelImpl
import st.orm.kt.template.impl.ORMTemplateImpl
import st.orm.kt.template.impl.QueryBuilderImpl
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 */
class EntityRepositoryImpl<E, ID : Any>(
    private val core: st.orm.core.repository.EntityRepository<E, ID>
) : EntityRepository<E, ID> where E : Record, E : Entity<ID> {

    override val model: Model<E, ID>
        get() = ModelImpl(core.model())

    override val orm: ORMTemplate
        get() = ORMTemplateImpl(core.orm())

    override fun ref(id: ID): Ref<E> {
        return core.ref(id)
    }

    override fun ref(entity: E): Ref<E> {
        return core.ref(entity)
    }

    override fun unload(entity: E): Ref<E> {
        return core.unload(entity)
    }

    override fun select(): QueryBuilder<E, E, ID> {
        return QueryBuilderImpl(core.select())
    }

    override fun selectCount(): QueryBuilder<E, Long, ID> {
        return QueryBuilderImpl(core.selectCount())
    }

    override fun <R : Any> select(selectType: KClass<R>): QueryBuilder<E, R, ID> {
        return QueryBuilderImpl(core.select(selectType.java))
    }

    override fun selectRef(): QueryBuilder<E, Ref<E>, ID> {
        return QueryBuilderImpl(core.selectRef())
    }

    override fun <R : Any> select(
        selectType: KClass<R>,
        template: TemplateString
    ): QueryBuilder<E, R, ID> {
        return QueryBuilderImpl(core.select(selectType.java, template.unwrap))
    }

    override fun <R : Record> selectRef(refType: KClass<R>): QueryBuilder<E, Ref<R>, ID> {
        return QueryBuilderImpl<E, Ref<R>, ID>(core.selectRef(refType.java))
    }

    override fun delete(): QueryBuilder<E, *, ID> {
        return QueryBuilderImpl(core.delete())
    }

    override fun count(): Long {
        return core.count()
    }

    override fun existsById(id: ID): Boolean {
        return core.existsById(id)
    }

    override fun existsByRef(ref: Ref<E>): Boolean {
        return core.existsByRef(ref)
    }

    override fun insert(entity: E) {
        core.insert(entity)
    }

    override fun insert(entity: E, ignoreAutoGenerate: Boolean) {
        core.insert(entity, ignoreAutoGenerate)
    }

    override fun insertAndFetchId(entity: E): ID {
        return core.insertAndFetchId(entity)
    }

    override fun insertAndFetch(entity: E): E {
        return core.insertAndFetch(entity)
    }

    override fun update(entity: E) {
        core.update(entity)
    }

    override fun updateAndFetch(entity: E): E {
        return core.updateAndFetch(entity)
    }

    override fun upsert(entity: E) {
        core.upsert(entity)
    }

    override fun upsertAndFetchId(entity: E): ID {
        return core.upsertAndFetchId(entity)
    }

    override fun upsertAndFetch(entity: E): E {
        return core.upsertAndFetch(entity)
    }

    override fun delete(entity: E) {
        core.delete(entity)
    }

    override fun deleteById(id: ID) {
        core.deleteById(id)
    }

    override fun deleteByRef(ref: Ref<E>) {
        core.deleteByRef(ref)
    }

    override fun deleteAll() {
        core.deleteAll()
    }

    override fun findById(id: ID): E? {
        return core.findById(id).orElse(null)
    }

    override fun findByRef(ref: Ref<E>): E? {
        return core.findByRef(ref).orElse(null)
    }

    override fun getById(id: ID): E {
        return core.getById(id)
    }

    override fun getByRef(ref: Ref<E>): E {
        return core.getByRef(ref)
    }

    override fun findAll(): MutableList<E> {
        return core.findAll()
    }

    override fun findAllById(ids: Iterable<ID>): MutableList<E> {
        return core.findAllById(ids)
    }

    override fun findAllByRef(refs: Iterable<Ref<E>>): MutableList<E> {
        return core.findAllByRef(refs)
    }

    override fun insert(entities: Iterable<E>) {
        core.insert(entities)
    }

    override fun insert(entities: Iterable<E>, ignoreAutoGenerate: Boolean) {
        core.insert(entities, ignoreAutoGenerate)
    }

    override fun insertAndFetchIds(entities: Iterable<E>): List<ID> {
        return core.insertAndFetchIds(entities)
    }

    override fun insertAndFetch(entities: Iterable<E>): List<E> {
        return core.insertAndFetch(entities)
    }

    override fun update(entities: Iterable<E>) {
        core.update(entities)
    }

    override fun updateAndFetch(entities: Iterable<E>): List<E> {
        return core.updateAndFetch(entities)
    }

    override fun upsert(entities: Iterable<E>) {
        core.upsert(entities)
    }

    override fun upsertAndFetchIds(entities: Iterable<E>): List<ID> {
        return core.upsertAndFetchIds(entities)
    }

    override fun upsertAndFetch(entities: Iterable<E>): List<E> {
        return core.upsertAndFetch(entities)
    }

    override fun delete(entities: Iterable<E>) {
        core.delete(entities)
    }

    override fun deleteByRef(refs: Iterable<Ref<E>>) {
        core.deleteByRef(refs)
    }

    override fun selectAll(): Stream<E> {
        return core.selectAll()
    }

    override fun selectById(ids: Stream<ID>): Stream<E> {
        return core.selectById(ids)
    }

    override fun selectByRef(refs: Stream<Ref<E>>): Stream<E> {
        return core.selectByRef(refs)
    }

    override fun selectById(ids: Stream<ID>, batchSize: Int): Stream<E> {
        return core.selectById(ids, batchSize)
    }

    override fun selectByRef(refs: Stream<Ref<E>>, batchSize: Int): Stream<E> {
        return core.selectByRef(refs, batchSize)
    }

    override fun countById(ids: Stream<ID>): Long {
        return core.countById(ids)
    }

    override fun countById(ids: Stream<ID>, batchSize: Int): Long {
        return core.countById(ids, batchSize)
    }

    override fun countByRef(refs: Stream<Ref<E>>): Long {
        return core.countByRef(refs)
    }

    override fun countByRef(refs: Stream<Ref<E>>, batchSize: Int): Long {
        return core.countByRef(refs, batchSize)
    }

    override fun insert(entities: Stream<E>) {
        core.insert(entities)
    }

    override fun insert(entities: Stream<E>, ignoreAutoGenerate: Boolean) {
        core.insert(entities, ignoreAutoGenerate)
    }

    override fun insert(entities: Stream<E>, batchSize: Int) {
        core.insert(entities, batchSize)
    }

    override fun insert(entities: Stream<E>, batchSize: Int, ignoreAutoGenerate: Boolean) {
        core.insert(entities, batchSize, ignoreAutoGenerate)
    }

    override fun insertAndFetchIds(entities: Stream<E>, callback: BatchCallback<ID>) {
        core.insertAndFetchIds(
            entities
        ) { batch: Stream<ID> -> callback.process(batch) }
    }

    override fun insertAndFetch(entities: Stream<E>, callback: BatchCallback<E>) {
        core.insertAndFetch(
            entities
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun insertAndFetchIds(
        entities: Stream<E>,
        batchSize: Int,
        callback: BatchCallback<ID>
    ) {
        core.insertAndFetchIds(
            entities,
            batchSize
        ) { batch: Stream<ID> -> callback.process(batch) }
    }

    override fun insertAndFetch(entities: Stream<E>, batchSize: Int, callback: BatchCallback<E>) {
        core.insertAndFetch(
            entities,
            batchSize
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun update(entities: Stream<E>) {
        core.update(entities)
    }

    override fun update(entities: Stream<E>, batchSize: Int) {
        core.update(entities, batchSize)
    }

    override fun updateAndFetch(entities: Stream<E>, callback: BatchCallback<E>) {
        core.updateAndFetch(
            entities
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun updateAndFetch(entities: Stream<E>, batchSize: Int, callback: BatchCallback<E>) {
        core.updateAndFetch(
            entities,
            batchSize
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun upsert(entities: Stream<E>) {
        core.upsert(entities)
    }

    override fun upsert(entities: Stream<E>, batchSize: Int) {
        core.upsert(entities, batchSize)
    }

    override fun upsertAndFetchIds(entities: Stream<E>, callback: BatchCallback<ID>) {
        core.upsertAndFetchIds(
            entities
        ) { batch: Stream<ID> -> callback.process(batch) }
    }

    override fun upsertAndFetch(entities: Stream<E>, callback: BatchCallback<E>) {
        core.upsertAndFetch(
            entities
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun upsertAndFetchIds(
        entities: Stream<E>,
        batchSize: Int,
        callback: BatchCallback<ID>
    ) {
        core.upsertAndFetchIds(
            entities,
            batchSize
        ) { batch: Stream<ID> -> callback.process(batch) }
    }

    override fun upsertAndFetch(entities: Stream<E>, batchSize: Int, callback: BatchCallback<E>) {
        core.upsertAndFetch(
            entities,
            batchSize
        ) { batch: Stream<E> -> callback.process(batch) }
    }

    override fun delete(entities: Stream<E>) {
        core.delete(entities)
    }

    override fun delete(entities: Stream<E>, batchSize: Int) {
        core.delete(entities, batchSize)
    }

    override fun deleteByRef(refs: Stream<Ref<E>>) {
        core.deleteByRef(refs)
    }

    override fun deleteByRef(refs: Stream<Ref<E>>, batchSize: Int) {
        core.deleteByRef(refs, batchSize)
    }
}
