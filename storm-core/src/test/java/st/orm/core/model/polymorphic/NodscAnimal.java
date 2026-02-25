package st.orm.core.model.polymorphic;

import static st.orm.Polymorphic.Strategy.JOINED;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Polymorphic;

/**
 * Joined Table Inheritance without @Discriminator.
 * Type resolution is done via CASE expression checking extension table PKs.
 */
@Polymorphic(JOINED)
@DbTable("nodsc_animal")
public sealed interface NodscAnimal extends Entity<Integer> permits NodscCat, NodscDog, NodscBird {
}
