package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.PK;

/**
 * Dog subtype for joined table inheritance without discriminator.
 */
@DbTable("nodsc_dog")
public record NodscDog(
        @PK Integer id,
        String name,
        int weight
) implements NodscAnimal {
}
