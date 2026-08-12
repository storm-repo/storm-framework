package st.orm.ktor.vet

import st.orm.repository.EntityRepository

internal interface VetRepository : EntityRepository<Vet, Int>
