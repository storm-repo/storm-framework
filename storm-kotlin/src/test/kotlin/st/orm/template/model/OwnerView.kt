package st.orm.template.model

import st.orm.DbTable
import st.orm.PK
import st.orm.Projection
import st.orm.Version

@DbTable("owner_view")
data class OwnerView(
    @PK val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val address: Address,
    val telephone: String?,
    @Version val version: Int
) : Projection<Int>
