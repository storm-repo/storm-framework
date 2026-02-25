package st.orm.template.model

import st.orm.DbTable
import st.orm.Discriminator
import st.orm.Discriminator.DiscriminatorType.CHAR
import st.orm.Entity
import st.orm.PK

@Discriminator(type = CHAR)
@DbTable("char_disc_animal")
sealed interface CharDiscAnimal : Entity<Int>

@Discriminator("C")
data class CharDiscCat(
    @PK val id: Int = 0,
    val name: String,
    val indoor: Boolean,
) : CharDiscAnimal

@Discriminator("D")
data class CharDiscDog(
    @PK val id: Int = 0,
    val name: String,
    val weight: Int,
) : CharDiscAnimal
