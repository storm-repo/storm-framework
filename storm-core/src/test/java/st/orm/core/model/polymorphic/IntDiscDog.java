package st.orm.core.model.polymorphic;

import st.orm.Discriminator;
import st.orm.PK;

/**
 * Dog subtype for integer-discriminated single-table inheritance.
 */
@Discriminator("2")
public record IntDiscDog(
        @PK Integer id,
        String name,
        int weight
) implements IntDiscAnimal {
}
