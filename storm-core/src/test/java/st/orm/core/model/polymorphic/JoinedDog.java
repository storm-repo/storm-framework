package st.orm.core.model.polymorphic;

import st.orm.PK;

/**
 * Dog subtype for joined table inheritance.
 */
public record JoinedDog(
        @PK Integer id,
        String name,
        int weight
) implements JoinedAnimal {
}
