package st.orm.kotlin.model

import st.orm.FK
import st.orm.Name
import st.orm.PK
import st.orm.Persist
import st.orm.kotlin.repository.KEntity
import java.time.LocalDate

@Name("pet")
@JvmRecord
data class KotlinPet(@PK val id: Int = 0,
                     val name: String,
                     @Persist(updatable = false) @Name("birth_date") val birthDate: LocalDate,
                     @Persist(updatable = false) @FK @Name("type_id") val petType: PetType,
                     @Name("owner_id") @FK val owner: KotlinOwner? = null
) : KEntity<Int> {
}
