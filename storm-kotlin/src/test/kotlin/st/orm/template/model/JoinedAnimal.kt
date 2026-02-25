package st.orm.template.model

import st.orm.DbTable
import st.orm.Discriminator
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Polymorphic
import st.orm.Polymorphic.Strategy.JOINED
import st.orm.Ref

@Discriminator
@Polymorphic(JOINED)
@DbTable("joined_animal")
sealed interface JoinedAnimal : Entity<Int>

@DbTable("joined_cat")
data class JoinedCat(
    @PK val id: Int = 0,
    val name: String,
    val indoor: Boolean,
) : JoinedAnimal

@DbTable("joined_dog")
data class JoinedDog(
    @PK val id: Int = 0,
    val name: String,
    val weight: Int,
) : JoinedAnimal

@DbTable("joined_adoption")
data class JoinedAdoption(
    @PK val id: Int = 0,
    @FK val animal: Ref<JoinedAnimal>,
) : Entity<Int>
