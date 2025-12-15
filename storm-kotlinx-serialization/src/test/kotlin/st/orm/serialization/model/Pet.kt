package st.orm.serialization.model

import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Persist
import java.time.LocalDate

/**
 * Simple business object representing a pet.
 */
data class Pet(
    @PK val id: Int = 0,
    val name: String,
    @Persist(updatable = false) val birthDate: LocalDate,
    @FK @Persist(updatable = false) val type: PetType,
    @FK val owner: Owner? = null
) : Entity<Int>