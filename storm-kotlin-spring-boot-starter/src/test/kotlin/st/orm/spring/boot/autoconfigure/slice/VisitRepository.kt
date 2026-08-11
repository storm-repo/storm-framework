package st.orm.spring.boot.autoconfigure.slice

import st.orm.repository.EntityRepository

internal interface VisitRepository : EntityRepository<Visit, Int>
