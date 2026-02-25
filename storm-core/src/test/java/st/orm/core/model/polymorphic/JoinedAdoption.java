package st.orm.core.model.polymorphic;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

/**
 * Adoption entity with FK to JoinedAnimal (joined table inheritance).
 */
@DbTable("joined_adoption")
public record JoinedAdoption(
        @PK Integer id,
        @FK Ref<JoinedAnimal> animal
) implements Entity<Integer> {
}
