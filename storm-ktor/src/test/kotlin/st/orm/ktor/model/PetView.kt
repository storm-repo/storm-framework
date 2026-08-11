package st.orm.ktor.model

import st.orm.DbTable
import st.orm.PK
import st.orm.Projection

@DbTable("pet")
internal data class PetView(
    @PK val id: Int = 0,
    val name: String,
) : Projection<Int>
