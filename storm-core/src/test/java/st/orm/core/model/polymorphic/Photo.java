package st.orm.core.model.polymorphic;

import st.orm.Entity;
import st.orm.PK;

/**
 * Photo subtype for polymorphic FK.
 */
public record Photo(
        @PK Integer id,
        String url
) implements Commentable, Entity<Integer> {
}
