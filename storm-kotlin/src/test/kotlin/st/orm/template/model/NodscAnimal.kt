package st.orm.template.model

import st.orm.DbTable
import st.orm.Entity
import st.orm.PK
import st.orm.Polymorphic
import st.orm.Polymorphic.Strategy.JOINED

@Polymorphic(JOINED)
@DbTable("nodsc_animal")
internal sealed interface NodscAnimal : Entity<Int> {
    val id: Int
    val name: String
}

@DbTable("nodsc_cat")
internal data class NodscCat(
    @PK override val id: Int = 0,
    override val name: String,
    val indoor: Boolean,
) : NodscAnimal

@DbTable("nodsc_dog")
internal data class NodscDog(
    @PK override val id: Int = 0,
    override val name: String,
    val weight: Int,
) : NodscAnimal

@DbTable("nodsc_bird")
internal data class NodscBird(
    @PK override val id: Int = 0,
    override val name: String,
) : NodscAnimal
