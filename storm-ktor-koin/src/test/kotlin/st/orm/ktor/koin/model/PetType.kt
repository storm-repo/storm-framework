package st.orm.ktor.koin.model

import st.orm.Entity
import st.orm.GenerationStrategy
import st.orm.PK

data class PetType(
    @PK(generation = GenerationStrategy.NONE) val id: Int = 0,
    val name: String,
) : Entity<Int>
