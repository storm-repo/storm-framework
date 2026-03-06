package st.orm.spring.repository

import st.orm.Metamodel

/**
 * Simulates a generated metamodel interface in the same package as repositories.
 * This should NOT be picked up by [st.orm.spring.RepositoryBeanFactoryPostProcessor].
 */
interface PetMetamodel : Metamodel<st.orm.spring.model.Pet, st.orm.spring.model.Pet>
