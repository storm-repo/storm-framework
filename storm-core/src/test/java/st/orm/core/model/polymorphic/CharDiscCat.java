package st.orm.core.model.polymorphic;

import st.orm.Discriminator;
import st.orm.PK;

/**
 * Cat subtype for char-discriminated single-table inheritance.
 */
@Discriminator("C")
public record CharDiscCat(
        @PK Integer id,
        String name,
        boolean indoor
) implements CharDiscAnimal {
}
