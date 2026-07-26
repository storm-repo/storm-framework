package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;
import st.orm.Version;

/**
 * Maps the owner table with the city foreign key declared as a reference. Together with {@link PetOwnerCityRef} this
 * exercises a navigation chain that crosses two reference boundaries: Pet to Owner via a Ref, Owner to City via a Ref.
 */
@DbTable("owner")
public record OwnerCityRef(
        @PK Integer id,
        @Nonnull String firstName,
        @Nonnull String lastName,
        @Nonnull String address,
        @Nullable @FK @DbColumn("city_id") Ref<City> city,
        @Nullable String telephone,
        @Version int version
) implements Entity<Integer> {}
