package st.orm.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Qualifier;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.FK;
import st.orm.PK;
import st.orm.repository.Entity;

import java.time.LocalDate;

@Builder(toBuilder = true)
@DbTable("visit")
public record VisitWithTwoPets(
        @PK Integer id,
        @Nonnull @DbColumn("visit_date") LocalDate visitDate,
        @Nullable String description,
        @FK @DbColumn("pet_id") @Qualifier("mom") PetOwnerRef pet1,
        @FK @DbColumn("pet_id") @Qualifier("dad") PetOwnerRef pet2
) implements Entity<Integer> {
}
