package st.orm.core.model.polymorphic;

import st.orm.PK;

/**
 * Dog subtype for single-table inheritance.
 */
public record Dog(
        @PK Integer id,
        String name,
        int weight
) implements Animal {
}
