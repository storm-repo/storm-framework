package st.orm.template.model

import st.orm.DbTable
import st.orm.Entity
import st.orm.PK
import st.orm.Polymorphic
import st.orm.Polymorphic.Strategy.JOINED

@Polymorphic(JOINED)
@DbTable("nodsc_animal")
sealed interface NodscAnimal : Entity<Int> {
    val id: Int
    val name: String
}

@DbTable("nodsc_cat")
data class NodscCat(
    @PK override val id: Int = 0,
    override val name: String,
    val indoor: Boolean,
) : NodscAnimal

@DbTable("nodsc_dog")
data class NodscDog(
    @PK override val id: Int = 0,
    override val name: String,
    val weight: Int,
) : NodscAnimal

@DbTable("nodsc_bird")
data class NodscBird(
    @PK override val id: Int = 0,
    override val name: String,
) : NodscAnimal
