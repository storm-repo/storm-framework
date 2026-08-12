package st.orm.spring.repository

import st.orm.repository.EntityRepository
import st.orm.spring.model.Visit

internal interface VisitRepository : EntityRepository<Visit, Int>
