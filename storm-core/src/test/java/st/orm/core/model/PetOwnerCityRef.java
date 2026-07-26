package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Ref;

/**
 * Maps the pet table with the owner foreign key declared as a reference to {@link OwnerCityRef}, whose own city foreign
 * key is also a reference. Navigating owner.city.name therefore crosses two reference boundaries in a single path.
 */
@DbTable("pet")
public record PetOwnerCityRef(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @DbColumn("type_id") @Persist(updatable = false) PetType petType,
        @Nullable @FK @DbColumn("owner_id") Ref<OwnerCityRef> owner
) implements Entity<Integer> {}
