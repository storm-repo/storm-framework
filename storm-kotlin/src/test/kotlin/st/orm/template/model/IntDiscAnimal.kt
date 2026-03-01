package st.orm.template.model

import st.orm.DbTable
import st.orm.Discriminator
import st.orm.Discriminator.DiscriminatorType.INTEGER
import st.orm.Entity
import st.orm.PK

@Discriminator(type = INTEGER)
@DbTable("int_disc_animal")
sealed interface IntDiscAnimal : Entity<Int>

@Discriminator("1")
data class IntDiscCat(
    @PK val id: Int = 0,
    val name: String,
    val indoor: Boolean,
) : IntDiscAnimal

@Discriminator("2")
data class IntDiscDog(
    @PK val id: Int = 0,
    val name: String,
    val weight: Int,
) : IntDiscAnimal
