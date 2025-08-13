package st.orm.spring.repository;

import st.orm.repository.EntityRepository;
import st.orm.spring.NoRepositoryBean;
import st.orm.spring.model.Owner;

@NoRepositoryBean
public interface OwnerRepository extends EntityRepository<Owner, Integer> {
}
