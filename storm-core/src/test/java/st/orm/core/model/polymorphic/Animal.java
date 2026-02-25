package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Discriminator;
import st.orm.Entity;

/**
 * Single-Table Inheritance.
 * Sealed interface as entity with @DbTable. Subtypes do NOT have @DbTable.
 */
@Discriminator
@DbTable("animal")
public sealed interface Animal extends Entity<Integer> permits Cat, Dog {
}
