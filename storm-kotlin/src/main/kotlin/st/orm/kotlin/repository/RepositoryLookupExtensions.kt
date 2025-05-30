package st.orm.kotlin.repository

import st.orm.repository.Entity
import st.orm.repository.EntityRepository
import st.orm.repository.Projection
import st.orm.repository.ProjectionRepository
import st.orm.repository.Repository
import st.orm.repository.RepositoryLookup

/**
 * Extensions for [RepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified T> RepositoryLookup.entity(): EntityRepository<T, *> where T : Record, T : Entity<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("entity", Class::class.java)
    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, T::class.java) as EntityRepository<T, *>
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T> RepositoryLookup.projection(): ProjectionRepository<T, *> where T : Record, T : Projection<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("entity", Class::class.java)
    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, T::class.java) as ProjectionRepository<T, *>
}

/**
 * Extensions for [RepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified R : Repository> RepositoryLookup.repository(): R {
    return repository(R::class.java)
}
