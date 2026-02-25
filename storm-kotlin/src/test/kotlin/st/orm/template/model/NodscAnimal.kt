package st.orm.template.model

import st.orm.DbTable
import st.orm.Entity
import st.orm.PK
import st.orm.Polymorphic
import st.orm.Polymorphic.Strategy.JOINED

@Polymorphic(JOINED)
@DbTable("nodsc_animal")
sealed interface NodscAnimal : Entity<Int>

@DbTable("nodsc_cat")
data class NodscCat(
    @PK val id: Int = 0,
    val name: String,
    val indoor: Boolean,
) : NodscAnimal

@DbTable("nodsc_dog")
data class NodscDog(
    @PK val id: Int = 0,
    val name: String,
    val weight: Int,
) : NodscAnimal

@DbTable("nodsc_bird")
data class NodscBird(
    @PK val id: Int = 0,
    val name: String,
) : NodscAnimal
