package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** The other half of {@link CycleA}'s key chain. */
public record CycleB(@PK(generation = NONE) @FK CycleA other) implements Entity<CycleA> {}
