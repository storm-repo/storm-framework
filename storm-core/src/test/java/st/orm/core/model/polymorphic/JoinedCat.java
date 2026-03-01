package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.PK;

/**
 * Cat subtype for joined table inheritance.
 */
@DbTable("joined_cat")
public record JoinedCat(
        @PK Integer id,
        String name,
        boolean indoor
) implements JoinedAnimal {
}
