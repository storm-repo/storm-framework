package st.orm.spring.repository;

import st.orm.Metamodel;
import st.orm.spring.model.Pet;

/**
 * Simulates a generated metamodel interface in the same package as repositories.
 * This should NOT be picked up by {@link st.orm.spring.RepositoryBeanFactoryPostProcessor}.
 */
public interface PetMetamodel extends Metamodel<Pet, Pet> {
}
