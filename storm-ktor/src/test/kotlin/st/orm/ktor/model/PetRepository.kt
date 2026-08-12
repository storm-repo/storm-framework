package st.orm.ktor.model

import st.orm.repository.EntityRepository

internal interface PetRepository : EntityRepository<Pet, Int>
