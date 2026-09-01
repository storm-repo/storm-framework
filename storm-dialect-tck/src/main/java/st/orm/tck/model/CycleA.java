package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** Half of a key chain that references itself, which the model must refuse to flatten. */
public record CycleA(@PK(generation = NONE) @FK CycleB other) implements Entity<CycleB> {}
