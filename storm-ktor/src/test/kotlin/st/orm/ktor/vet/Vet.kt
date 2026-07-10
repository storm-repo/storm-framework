package st.orm.ktor.vet

import st.orm.Entity
import st.orm.PK

data class Vet(
    @PK val id: Int = 0,
    val firstName: String,
    val lastName: String,
) : Entity<Int>
