package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

/**
 * Adoption entity with FK to Animal (single-table inheritance).
 */
@DbTable("adoption")
public record Adoption(
        @PK Integer id,
        @FK Ref<Animal> animal
) implements Entity<Integer> {
}
