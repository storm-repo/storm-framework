package com.example.scope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import st.orm.template.ORMTemplate
import st.orm.template.model.PetOwnerRef
import st.orm.template.sqlScopeContext

/**
 * A database layer the way an application writes one: an inline extension whose lambdas compile into the
 * caller's class under a `$$inlined$` name while their frames keep this file's name. A package prefix cannot
 * see those frames; a file entry can.
 */
suspend fun <T> passthrough(block: suspend () -> T): T = block()

suspend inline fun <T> throughDbLayer(crossinline block: suspend () -> T): T = passthrough { block() }

/**
 * A fan-out the way an application's database layer writes one: the work runs on another dispatcher, whose
 * stack starts at the dispatcher and goes straight into this file, with the caller nowhere on it.
 */
suspend fun fetchAllOnDispatcher(orm: ORMTemplate): List<PetOwnerRef> = withContext(Dispatchers.Default + sqlScopeContext()) {
    orm.entity(PetOwnerRef::class).select().resultList
}
