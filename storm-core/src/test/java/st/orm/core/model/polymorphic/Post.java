package st.orm.core.model.polymorphic;

import st.orm.Entity;
import st.orm.PK;

/**
 * Post subtype for polymorphic FK.
 */
public record Post(
        @PK Integer id,
        String title
) implements Commentable, Entity<Integer> {
}
