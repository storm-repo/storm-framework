package st.orm.core.model;

import java.time.LocalDateTime;
import st.orm.Entity;
import st.orm.PK;

/**
 * Entity whose {@code scheduled_at} column is declared with second precision, so a database round-trip drops any
 * sub-second part of the in-memory value and the two representations are not structurally equal.
 */
public record Appointment(
        @PK Integer id,
        String description,
        LocalDateTime scheduledAt
) implements Entity<Integer> {}
