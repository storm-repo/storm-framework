package st.orm.core.model;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Ref;

@Builder(toBuilder = true)
@DbTable("pet")
public record PetOwnerRef(
        @PK Integer id,
        String name,
        @Persist(updatable = false) LocalDate birthDate,
        @FK @DbColumn("type_id") @Persist(updatable = false) PetType petType,
        @Nullable @FK @DbColumn("owner_id") Ref<Owner> owner
) implements Entity<Integer> {
}
