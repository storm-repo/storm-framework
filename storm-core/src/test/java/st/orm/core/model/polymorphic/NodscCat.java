package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.PK;

/**
 * Cat subtype for joined table inheritance without discriminator.
 */
@DbTable("nodsc_cat")
public record NodscCat(
        @PK Integer id,
        String name,
        boolean indoor
) implements NodscAnimal {
}
