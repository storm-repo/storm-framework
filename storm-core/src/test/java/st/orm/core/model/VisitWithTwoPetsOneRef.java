package st.orm.core.model;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPetsOneRef(
        @PK Integer id,
        @DbColumn("visit_date") LocalDate visitDate,
        @Nullable String description,
        @Nullable @FK @DbColumn("pet_id") PetOwnerRef pet1,
        @Nullable @FK @DbColumn("pet_id") Ref<PetOwnerRef> pet2
) implements Entity<Integer> {
}
