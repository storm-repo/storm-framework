package st.orm.kotlin.model

import st.orm.*
import st.orm.kotlin.repository.KEntity
import java.time.LocalDate

@JvmRecord
@DbTable("pet")
data class KotlinPet(@PK val id: Int = 0,
                     val name: String,
                     @Persist(updatable = false) val birthDate: LocalDate,
                     @Persist(updatable = false) @FK @DbColumn("type_id") val petType: PetType,
                     @FK val owner: KotlinOwner? = null
) : KEntity<Int>
