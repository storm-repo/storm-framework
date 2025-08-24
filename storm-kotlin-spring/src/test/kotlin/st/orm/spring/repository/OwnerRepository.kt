package st.orm.spring.repository

import st.orm.repository.EntityRepository
import st.orm.spring.NoRepositoryBean
import st.orm.spring.model.Owner

@NoRepositoryBean
interface OwnerRepository : EntityRepository<Owner, Int> {
}