package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Discriminator.DiscriminatorType;
import st.orm.Entity;

/**
 * Single-Table Inheritance with CHAR discriminator type.
 */
@Discriminator(type = DiscriminatorType.CHAR)
@DbTable("char_disc_animal")
public sealed interface CharDiscAnimal extends Entity<Integer> permits CharDiscCat, CharDiscDog {
}
