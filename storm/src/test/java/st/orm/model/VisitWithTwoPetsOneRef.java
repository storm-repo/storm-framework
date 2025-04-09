package st.orm.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.Ref;
import st.orm.PK;
import st.orm.repository.Entity;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPetsOneRef(
        @PK Integer id,
        @Nonnull @DbColumn("visit_date") LocalDate visitDate,
        @Nullable String description,
        @Nullable @FK @DbColumn("pet_id") PetOwnerRef pet1,
        @Nullable @FK @DbColumn("pet_id") Ref<PetOwnerRef> pet2
) implements Entity<Integer> {
}
