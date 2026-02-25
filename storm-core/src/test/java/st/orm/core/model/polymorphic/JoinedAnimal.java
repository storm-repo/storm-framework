package st.orm.core.model.polymorphic;

import static st.orm.Polymorphic.Strategy.JOINED;

import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Entity;
import st.orm.Polymorphic;

/**
 * Joined Table Inheritance.
 * Sealed interface as entity with @Polymorphic(JOINED).
 */
@Discriminator
@Polymorphic(JOINED)
@DbTable("joined_animal")
public sealed interface JoinedAnimal extends Entity<Integer> permits JoinedCat, JoinedDog {
}
