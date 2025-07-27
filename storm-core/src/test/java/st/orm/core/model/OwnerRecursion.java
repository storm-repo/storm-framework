package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

@Builder(toBuilder = true)
@DbTable("owner")
public record OwnerRecursion(
        @PK Integer id,
        @Nonnull String firstName,
        @Nonnull String lastName,
        @Nonnull Address address,
        @Nullable String telephone,
        @FK PetOwnerRecursion pet   // Recursive reference; We can test this even though the column does not exist.
) implements Entity<Integer> {
}
