package st.orm.spring.repository

import st.orm.repository.EntityRepository
import st.orm.spring.model.Visit

interface VisitRepository : EntityRepository<Visit, Int> {
}