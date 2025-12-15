package st.orm.serialization.model

import st.orm.Entity
import st.orm.Json
import st.orm.PK

/**
 * Simple domain object representing an owner.
 */
data class Owner(
    @PK val id: Int = 0,
    override val firstName: String,
    override val lastName: String,
    @Json val address: Address,
    val telephone: String?
) : Entity<Int>, Person
