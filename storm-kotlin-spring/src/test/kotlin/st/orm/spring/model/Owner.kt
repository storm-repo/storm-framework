package st.orm.spring.model

import st.orm.Entity
import st.orm.PK
import st.orm.Version

/**
 * Simple domain object representing an owner.
 *
 */
@JvmRecord
data class Owner(
    @PK val id: Int = 0,
    override val firstName: String,
    override val lastName: String,
    val address: Address,
    val telephone: String?,
    @Version val version: Int
) : Entity<Int>, Person
