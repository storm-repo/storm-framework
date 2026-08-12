package st.orm.spring.repository

import st.orm.Metamodel

/**
 * Simulates a generated metamodel interface in the same package as repositories.
 * This should NOT be picked up by [st.orm.spring.RepositoryBeanFactoryPostProcessor].
 *
 * Deliberately has no code references: it is exercised through classpath scanning, and
 * `RepositoryTest` asserts by bean name that no bean was registered for it.
 */
internal interface PetMetamodel : Metamodel<st.orm.spring.model.Pet, st.orm.spring.model.Pet>
