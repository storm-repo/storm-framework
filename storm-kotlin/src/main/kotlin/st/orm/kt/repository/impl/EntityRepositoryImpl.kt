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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.stream.consumeAsFlow
import st.orm.Entity
import st.orm.Ref
import st.orm.kt.repository.EntityRepository
import st.orm.kt.template.*
import st.orm.kt.template.impl.ModelImpl
import st.orm.kt.template.impl.ORMTemplateImpl
import st.orm.kt.template.impl.QueryBuilderImpl
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

    override fun ref(id: ID): Ref<E> =
        core.ref(id)

    override fun ref(entity: E): Ref<E> =
        core.ref(entity)

    override fun unload(entity: E): Ref<E> =
        core.unload(entity)

    override fun select(): QueryBuilder<E, E, ID> =
        QueryBuilderImpl(core.select())

    override fun selectCount(): QueryBuilder<E, Long, ID> =
        QueryBuilderImpl(core.selectCount())

    override fun <R : Any> select(selectType: KClass<R>): QueryBuilder<E, R, ID> =
        QueryBuilderImpl(core.select(selectType.java))

    override fun selectRef(): QueryBuilder<E, Ref<E>, ID> =
        QueryBuilderImpl(core.selectRef())

    override fun <R : Any> select(
        selectType: KClass<R>,
        template: TemplateString
    ): QueryBuilder<E, R, ID> =
        QueryBuilderImpl(core.select(selectType.java, template.unwrap))

    override fun <R : Record> selectRef(refType: KClass<R>): QueryBuilder<E, Ref<R>, ID> =
        QueryBuilderImpl<E, Ref<R>, ID>(core.selectRef(refType.java))

    override fun delete(): QueryBuilder<E, *, ID> =
        QueryBuilderImpl(core.delete())

    override fun count(): Long =
        core.count()

    override fun exists(): Boolean =
        core.exists()

    override fun existsById(id: ID): Boolean =
        core.existsById(id)

    override fun existsByRef(ref: Ref<E>): Boolean =
        core.existsByRef(ref)

    override fun insert(entity: E) =
        core.insert(entity)

    override fun insert(entity: E, ignoreAutoGenerate: Boolean) =
        core.insert(entity, ignoreAutoGenerate)

    override fun insertAndFetchId(entity: E): ID =
        core.insertAndFetchId(entity)

    override fun insertAndFetch(entity: E): E =
        core.insertAndFetch(entity)

    override fun update(entity: E) =
        core.update(entity)

    override fun updateAndFetch(entity: E): E =
        core.updateAndFetch(entity)

    override fun upsert(entity: E) =
        core.upsert(entity)

    override fun upsertAndFetchId(entity: E): ID =
        core.upsertAndFetchId(entity)

    override fun upsertAndFetch(entity: E): E =
        core.upsertAndFetch(entity)

    override fun delete(entity: E) =
        core.delete(entity)

    override fun deleteById(id: ID) =
        core.deleteById(id)

    override fun deleteByRef(ref: Ref<E>) =
        core.deleteByRef(ref)

    override fun deleteAll() =
        core.deleteAll()

    override fun findById(id: ID): E? =
        core.findById(id).orElse(null)

    override fun findByRef(ref: Ref<E>): E? =
        core.findByRef(ref).orElse(null)

    override fun getById(id: ID): E =
        core.getById(id)

    override fun getByRef(ref: Ref<E>): E =
        core.getByRef(ref)

    override fun findAll(): List<E> =
        core.findAll()

    override fun findAllById(ids: Iterable<ID>): List<E> =
        core.findAllById(ids)

    override fun findAllByRef(refs: Iterable<Ref<E>>): List<E> =
        core.findAllByRef(refs)

    override fun insert(entities: Iterable<E>) =
        core.insert(entities)

    override fun insert(entities: Iterable<E>, ignoreAutoGenerate: Boolean) =
        core.insert(entities, ignoreAutoGenerate)

    override fun insertAndFetchIds(entities: Iterable<E>): List<ID> =
        core.insertAndFetchIds(entities)

    override fun insertAndFetch(entities: Iterable<E>): List<E> =
        core.insertAndFetch(entities)

    override fun update(entities: Iterable<E>) =
        core.update(entities)

    override fun updateAndFetch(entities: Iterable<E>): List<E> =
        core.updateAndFetch(entities)

    override fun upsert(entities: Iterable<E>) =
        core.upsert(entities)

    override fun upsertAndFetchIds(entities: Iterable<E>): List<ID> =
        core.upsertAndFetchIds(entities)

    override fun upsertAndFetch(entities: Iterable<E>): List<E> =
        core.upsertAndFetch(entities)

    override fun delete(entities: Iterable<E>) =
        core.delete(entities)

    override fun deleteByRef(refs: Iterable<Ref<E>>) =
        core.deleteByRef(refs)

    override fun selectAll(): Flow<E> =
        core.selectAll().consumeAsFlow()

    override fun selectById(ids: Flow<ID>): Flow<E> =
        ids.chunked(core.defaultChunkSize)
            .flatMapConcat { core.findAllById(it).asFlow() }

    override fun selectByRef(refs: Flow<Ref<E>>): Flow<E> =
        refs.chunked(core.defaultChunkSize)
            .flatMapConcat { core.findAllByRef(it).asFlow() }

    override fun selectById(ids: Flow<ID>, chunkSize: Int): Flow<E> =
        ids.chunked(chunkSize)
            .flatMapConcat { core.findAllById(it).asFlow() }

    override fun selectByRef(refs: Flow<Ref<E>>, chunkSize: Int): Flow<E> =
        refs.chunked(chunkSize)
            .flatMapConcat { core.findAllByRef(it).asFlow() }

    override suspend fun countById(ids: Flow<ID>): Long =
        ids.chunked(core.defaultChunkSize)
            .map { chunk -> core.countById(chunk.stream()) }
            .fold(0L) { acc, v -> acc + v }

    override suspend fun countById(ids: Flow<ID>, chunkSize: Int): Long =
        ids.chunked(chunkSize)
            .map { chunk -> core.countById(chunk.stream()) }
            .fold(0L) { acc, v -> acc + v }

    override suspend fun countByRef(refs: Flow<Ref<E>>): Long =
        refs.chunked(core.defaultChunkSize)
            .map { chunk -> core.countByRef(chunk.stream()) }
            .fold(0L) { acc, v -> acc + v }

    override suspend fun countByRef(refs: Flow<Ref<E>>, chunkSize: Int): Long =
        refs.chunked(chunkSize)
            .map { chunk -> core.countByRef(chunk.stream()) }
            .fold(0L) { acc, v -> acc + v }

    override suspend fun insert(entities: Flow<E>) =
        entities.chunked(core.defaultBatchSize)
            .collect { core.insert(it) }

    override suspend fun insert(entities: Flow<E>, ignoreAutoGenerate: Boolean) =
        entities.chunked(core.defaultBatchSize)
            .collect { core.insert(it, ignoreAutoGenerate) }

    override suspend fun insert(entities: Flow<E>, batchSize: Int) =
        entities.chunked(batchSize)
            .collect { core.insert(it) }

    override suspend fun insert(entities: Flow<E>, batchSize: Int, ignoreAutoGenerate: Boolean) =
        entities.chunked(batchSize)
            .collect { core.insert(it, ignoreAutoGenerate) }

    override fun insertAndFetchIds(entities: Flow<E>): Flow<ID> =
        entities.chunked(core.defaultBatchSize)
            .flatMapConcat { core.insertAndFetchIds(it).asFlow() }

    override fun insertAndFetch(entities: Flow<E>): Flow<E> =
        entities.chunked(core.defaultBatchSize)
            .flatMapConcat { core.insertAndFetch(it).asFlow() }

    override fun insertAndFetchIds(
        entities: Flow<E>,
        batchSize: Int
    ): Flow<ID> =
        entities.chunked(batchSize)
            .flatMapConcat { core.insertAndFetchIds(it).asFlow() }

    override fun insertAndFetch(entities: Flow<E>, batchSize: Int): Flow<E> =
        entities.chunked(batchSize)
            .flatMapConcat { core.insertAndFetch(it).asFlow() }

    override suspend fun update(entities: Flow<E>) =
        entities.chunked(core.defaultBatchSize)
            .collect { core.update(it) }

    override suspend fun update(entities: Flow<E>, batchSize: Int) =
        entities.chunked(batchSize)
            .collect { core.update(it) }

    override fun updateAndFetch(entities: Flow<E>): Flow<E> =
        entities.chunked(core.defaultBatchSize)
            .flatMapConcat { core.updateAndFetch(it).asFlow() }

    override fun updateAndFetch(entities: Flow<E>, batchSize: Int): Flow<E> =
        entities.chunked(batchSize)
            .flatMapConcat { core.updateAndFetch(it).asFlow() }

    override suspend fun upsert(entities: Flow<E>) =
        entities.chunked(core.defaultBatchSize)
            .collect { core.upsert(it) }

    override suspend fun upsert(entities: Flow<E>, batchSize: Int) =
        entities.chunked(batchSize)
            .collect { core.upsert(it) }

    override fun upsertAndFetchIds(entities: Flow<E>): Flow<ID> =
        entities.chunked(core.defaultBatchSize)
            .flatMapConcat { core.upsertAndFetchIds(it).asFlow() }

    override fun upsertAndFetch(entities: Flow<E>): Flow<E> =
        entities.chunked(core.defaultBatchSize)
            .flatMapConcat { core.upsertAndFetch(it).asFlow() }

    override fun upsertAndFetchIds(
        entities: Flow<E>,
        batchSize: Int
    ): Flow<ID> =
        entities.chunked(batchSize)
            .flatMapConcat { core.upsertAndFetchIds(it).asFlow() }

    override fun upsertAndFetch(entities: Flow<E>, batchSize: Int): Flow<E> =
        entities.chunked(batchSize)
            .flatMapConcat { core.upsertAndFetch(it).asFlow() }

    override suspend fun delete(entities: Flow<E>) =
        entities.chunked(core.defaultBatchSize)
            .collect { core.delete(it) }

    override suspend fun delete(entities: Flow<E>, batchSize: Int) =
        entities.chunked(batchSize)
            .collect { core.delete(it) }

    override suspend fun deleteByRef(refs: Flow<Ref<E>>) =
        refs.chunked(core.defaultBatchSize)
            .collect { core.deleteByRef(it) }

    override suspend fun deleteByRef(refs: Flow<Ref<E>>, batchSize: Int) =
        refs.chunked(batchSize)
            .collect { core.deleteByRef(it) }
}
