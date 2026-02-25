package st.orm.template.model

import st.orm.DbTable
import st.orm.Discriminator
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Ref

@Discriminator
@DbTable("animal")
sealed interface Animal : Entity<Int>

data class Cat(
    @PK val id: Int = 0,
    val name: String,
    val indoor: Boolean,
) : Animal

data class Dog(
    @PK val id: Int = 0,
    val name: String,
    val weight: Int,
) : Animal

@DbTable("adoption")
data class Adoption(
    @PK val id: Int = 0,
    @FK val animal: Ref<Animal>,
) : Entity<Int>
