package st.orm.kt.spring.model

import st.orm.PK

/**
 * Can be Cat, Dog, Hamster...
 */
@JvmRecord
data class PetType(
    @PK val id: Int = 0,
    val name: String
)
