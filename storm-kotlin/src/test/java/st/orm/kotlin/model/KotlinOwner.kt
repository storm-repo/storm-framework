package st.orm.kotlin.model

import st.orm.DbName
import st.orm.PK
import st.orm.kotlin.repository.KEntity

@JvmRecord
@DbName("owner")
data class KotlinOwner(@PK val id: Int) : KEntity<Int> {
}
