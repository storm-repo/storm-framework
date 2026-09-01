package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Version;

/** A versioned entity whose key the caller supplies, so upsert cannot lean on a generated key to route itself. */
@Builder(toBuilder = true)
@DbTable("non_autogen_entity")
public record NonAutoGenEntity(
        @PK(generation = NONE) Integer id,
        String name,
        @Version int version
) implements Entity<Integer> {}
