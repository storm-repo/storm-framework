package st.orm.spring.model

import st.orm.Entity
import st.orm.PK

/**
 * Can be Cat, Dog, Hamster...
 */
@JvmRecord
data class PetType(
    @PK val id: Int = 0,
    val name: String
) : Entity<Int>
