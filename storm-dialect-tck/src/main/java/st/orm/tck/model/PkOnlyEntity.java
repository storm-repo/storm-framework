package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;

/** An entity whose only column is its key, which upsert has to handle without an update list. */
@Builder(toBuilder = true)
@DbTable("pk_only_entity")
public record PkOnlyEntity(
        @PK(generation = NONE) Integer id
) implements Entity<Integer> {}
