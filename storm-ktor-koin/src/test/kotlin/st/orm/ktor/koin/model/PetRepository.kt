package st.orm.ktor.koin.model

import st.orm.repository.EntityRepository

interface PetRepository : EntityRepository<Pet, Int>
