package st.orm.model;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.Ref;
import st.orm.PK;
import st.orm.Persist;
import st.orm.repository.Entity;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("pet")
public record PetOwnerRef(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @DbColumn("type_id") @Persist(updatable = false) PetType petType,
        @FK @DbColumn("owner_id") Ref<Owner> owner
) implements Entity<Integer> {
}
