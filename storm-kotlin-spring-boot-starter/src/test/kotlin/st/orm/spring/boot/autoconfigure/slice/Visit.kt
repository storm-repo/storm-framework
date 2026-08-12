package st.orm.spring.boot.autoconfigure.slice

import st.orm.Entity
import st.orm.PK

@JvmRecord
internal data class Visit(
    @PK val id: Int = 0,
    val description: String? = null,
) : Entity<Int>
