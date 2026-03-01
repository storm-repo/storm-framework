package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Discriminator.DiscriminatorType;
import st.orm.Entity;

/**
 * Single-Table Inheritance with INTEGER discriminator type.
 */
@Discriminator(type = DiscriminatorType.INTEGER)
@DbTable("int_disc_animal")
public sealed interface IntDiscAnimal extends Entity<Integer> permits IntDiscCat, IntDiscDog {
}
