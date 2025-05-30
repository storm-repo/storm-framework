package st.orm.kotlin.repository

import st.orm.repository.Entity
import st.orm.repository.Projection

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified T> KRepositoryLookup.entity(): KEntityRepository<T, *>
        where T : Record, T : Entity<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("entity", Class::class.java)
    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, T::class.java) as KEntityRepository<T, *>
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to projection repositories.
 */
inline fun <reified T> KRepositoryLookup.projection(): KProjectionRepository<T, *>
        where T : Record, T : Projection<*> {
    // Use reflection to prevent the need for the ID parameter. The compiler takes care of the type-safety but is
    // unable to infer the type of the ID parameter at compile time.
    val method = this::class.java.getMethod("projection", Class::class.java)
    @Suppress("UNCHECKED_CAST")
    return method.invoke(this, T::class.java) as KProjectionRepository<T, *>
}

/**
 * Extensions for [KRepositoryLookup] to provide convenient access to repositories.
 */
inline fun <reified R : KRepository> KRepositoryLookup.repository(): R {
    return repository(R::class)
}
