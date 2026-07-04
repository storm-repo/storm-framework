package st.orm.ktor.koin.model

import st.orm.Entity
import st.orm.FK
import st.orm.PK

data class Pet(
    @PK val id: Int = 0,
    val name: String,
    @FK val type: PetType,
) : Entity<Int>
