package st.orm.spring.repository

import org.springframework.data.repository.NoRepositoryBean
import st.orm.repository.EntityRepository
import st.orm.spring.model.Owner

@NoRepositoryBean
interface OwnerRepository : EntityRepository<Owner, Int> {
}