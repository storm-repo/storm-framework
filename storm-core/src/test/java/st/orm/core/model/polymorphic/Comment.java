package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

/**
 * Comment with polymorphic FK to Commentable.
 * The target field produces two columns: target_type + target_id.
 */
@DbTable("comment")
public record Comment(
        @PK Integer id,
        String text,
        @FK Ref<Commentable> target
) implements Entity<Integer> {
}
