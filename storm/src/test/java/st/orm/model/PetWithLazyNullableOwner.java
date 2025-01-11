package st.orm.model;

import jakarta.annotation.Nonnull;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PK;
import st.orm.Persist;
import st.orm.repository.Entity;

import java.time.LocalDate;

@DbTable("pet")
public record PetWithLazyNullableOwner(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @Persist(updatable = false) @DbColumn("type_id") PetType petType,
        @FK Lazy<Owner, Integer> owner
) implements Entity<Integer> {
}
