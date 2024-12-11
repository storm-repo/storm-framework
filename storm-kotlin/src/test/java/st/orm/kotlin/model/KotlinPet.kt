package st.orm.kotlin.model

import st.orm.FK
import st.orm.DbName
import st.orm.PK
import st.orm.Persist
import st.orm.kotlin.repository.KEntity
import java.time.LocalDate

@JvmRecord
@DbName("pet")
data class KotlinPet(@PK val id: Int = 0,
                     val name: String,
                     @Persist(updatable = false) @DbName("birth_date") val birthDate: LocalDate,
                     @Persist(updatable = false) @FK @DbName("type_id") val petType: PetType,
                     @DbName("owner_id") @FK val owner: KotlinOwner? = null
) : KEntity<Int>
