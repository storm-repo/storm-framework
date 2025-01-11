package st.orm.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.PK;
import st.orm.repository.Entity;

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
