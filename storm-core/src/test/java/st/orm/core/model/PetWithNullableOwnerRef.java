package st.orm.core.model;

import jakarta.annotation.Nonnull;
import java.time.LocalDate;
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
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @Persist(updatable = false) @DbColumn("type_id") PetType petType,
        @FK Ref<Owner> owner
) implements Entity<Integer> {
}
