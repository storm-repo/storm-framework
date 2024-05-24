package st.orm.kotlin.model

import st.orm.Name
import st.orm.PK
import st.orm.kotlin.repository.KEntity

@Name("owner")
@JvmRecord
data class KotlinOwner(@PK val id: Int) : KEntity<Int> {
}
