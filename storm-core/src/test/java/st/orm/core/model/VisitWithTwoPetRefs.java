package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPetRefs(
        @PK Integer id,
        @Nonnull LocalDate visitDate,
        @Nullable String description,
        @Nullable @FK @DbColumn("pet_id") Ref<PetOwnerRef> pet1,
        @Nullable @FK @DbColumn("pet_id") Ref<PetOwnerRef> pet2
) implements Entity<Integer> {
}
