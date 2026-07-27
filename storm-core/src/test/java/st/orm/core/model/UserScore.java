package st.orm.core.model;

import jakarta.annotation.Nonnull;
import java.time.LocalDate;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * A dated score for a user. Aggregating these per country and date reaches the country table through the user, two
 * foreign keys away from this record.
 */
public record UserScore(
        @PK Integer id,
        @Nonnull @FK User user,
        @Nonnull LocalDate scoreDate,
        double score
) implements Entity<Integer> {}
