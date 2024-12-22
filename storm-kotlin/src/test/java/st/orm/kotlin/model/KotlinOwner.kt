package st.orm.kotlin.model

import st.orm.DbTable
import st.orm.PK
import st.orm.kotlin.repository.KEntity

@JvmRecord
@DbTable("owner")
data class KotlinOwner(@PK val id: Int) : KEntity<Int> {
}
