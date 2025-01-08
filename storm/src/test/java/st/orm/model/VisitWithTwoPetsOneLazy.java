package st.orm.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.PK;
import st.orm.repository.Entity;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPetsOneLazy(
        @PK Integer id,
        @Nonnull @DbColumn("visit_date") LocalDate visitDate,
        @Nullable String description,
        @Nullable @FK @DbColumn("pet_id") PetLazyOwner pet1,
        @Nullable @FK @DbColumn("pet_id") Lazy<PetLazyOwner, Integer> pet2
) implements Entity<Integer> {
}
