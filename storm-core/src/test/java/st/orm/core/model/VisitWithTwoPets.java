package st.orm.core.model;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPets(
        @PK Integer id,
        @DbColumn("visit_date") LocalDate visitDate,
        @Nullable String description,
        @FK @DbColumn("pet_id") @Qualifier("mom") PetOwnerRef pet1,
        @FK @DbColumn("pet_id") @Qualifier("dad") PetOwnerRef pet2
) implements Entity<Integer> {
}
