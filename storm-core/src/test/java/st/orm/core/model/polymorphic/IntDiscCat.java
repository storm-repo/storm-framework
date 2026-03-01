package st.orm.core.model.polymorphic;

import st.orm.Discriminator;
import st.orm.PK;

/**
 * Cat subtype for integer-discriminated single-table inheritance.
 */
@Discriminator("1")
public record IntDiscCat(
        @PK Integer id,
        String name,
        boolean indoor
) implements IntDiscAnimal {
}
