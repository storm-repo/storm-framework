package st.orm.kotlin.model

import st.orm.Name
import st.orm.PK
import st.orm.kotlin.repository.KEntity

@JvmRecord
@Name("owner")
data class KotlinOwner(@PK val id: Int) : KEntity<Int> {
}
