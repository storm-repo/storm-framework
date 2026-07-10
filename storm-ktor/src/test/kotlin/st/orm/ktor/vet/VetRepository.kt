package st.orm.ktor.vet

import st.orm.repository.EntityRepository

interface VetRepository : EntityRepository<Vet, Int>
