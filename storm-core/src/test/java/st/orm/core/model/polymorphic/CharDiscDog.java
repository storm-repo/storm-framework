package st.orm.core.model.polymorphic;

import st.orm.Discriminator;
import st.orm.PK;

/**
 * Dog subtype for char-discriminated single-table inheritance.
 */
@Discriminator("D")
public record CharDiscDog(
        @PK Integer id,
        String name,
        int weight
) implements CharDiscAnimal {
}
