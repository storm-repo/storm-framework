package st.orm.serialization.model

import st.orm.Entity
import st.orm.PK

/**
 * Simple domain object representing a veterinarian.
 */
data class Vet(
    @PK val id: Int = 0,
    override val firstName: String,
    override val lastName: String,
) : Entity<Int>,
    Person
