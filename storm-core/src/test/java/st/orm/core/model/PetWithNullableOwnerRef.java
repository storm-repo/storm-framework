package st.orm.core.model;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Ref;

@DbTable("pet")
public record PetWithNullableOwnerRef(
        @PK Integer id,
        String name,
        @Persist(updatable = false) LocalDate birthDate,
        @FK @Persist(updatable = false) @DbColumn("type_id") PetType petType,
        @Nullable @FK Ref<Owner> owner
) implements Entity<Integer> {
}
