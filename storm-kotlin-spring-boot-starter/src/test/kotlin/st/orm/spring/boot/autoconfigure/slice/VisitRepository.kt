package st.orm.spring.boot.autoconfigure.slice

import st.orm.repository.EntityRepository

interface VisitRepository : EntityRepository<Visit, Int>
