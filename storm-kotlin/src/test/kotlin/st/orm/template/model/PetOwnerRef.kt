package st.orm.template.model

import st.orm.DbColumn
import st.orm.DbTable
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Persist
import st.orm.Ref
import java.time.LocalDate

/**
 * Maps the pet table with the owner declared as a reference, so the owner is selected as its foreign key column
 * rather than joined into every read. [Pet] maps the same table with the owner as an entity, which gives an
 * entity-graph baseline to compare a resolved reference against.
 */
@DbTable("pet")
data class PetOwnerRef(
    @PK val id: Int = 0,
    val name: String,
    @Persist(updatable = false) val birthDate: LocalDate,
    @FK @DbColumn("type_id") @Persist(updatable = false) val type: PetType,
    @FK @DbColumn("owner_id") val owner: Ref<Owner>? = null,
) : Entity<Int>
