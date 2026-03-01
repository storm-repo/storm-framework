package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.PK;

/**
 * Bird subtype for joined table inheritance without discriminator.
 * Has NO extension fields (tests PK-only extension table as type marker).
 */
@DbTable("nodsc_bird")
public record NodscBird(
        @PK Integer id,
        String name
) implements NodscAnimal {
}
