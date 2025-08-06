package st.orm.kt.spring.model

import st.orm.Entity
import st.orm.PK

/**
 * Simple domain object representing an owner.
 *
 */
@JvmRecord
data class City(
    @PK val id: Int = 0,
    val name: String
) : Entity<Int>
