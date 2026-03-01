package st.orm.core.model.polymorphic;

import st.orm.PK;

/**
 * Cat subtype for single-table inheritance.
 */
public record Cat(
        @PK Integer id,
        String name,
        boolean indoor
) implements Animal {
}
