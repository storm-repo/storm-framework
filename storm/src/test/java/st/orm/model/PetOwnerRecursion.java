package st.orm.model;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.PK;
import st.orm.repository.Entity;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("pet")
public record PetOwnerRecursion(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull LocalDate birthDate,
        @Nonnull @FK @DbColumn("type_id") PetType petType,
        @FK OwnerRecursion owner
) implements Entity<Integer> {
}
