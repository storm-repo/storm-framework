package st.orm.template.model

import st.orm.Entity
import st.orm.PK
import java.time.LocalDateTime

/**
 * Entity whose `scheduled_at` column is declared with second precision, so a database round-trip drops any
 * sub-second part of the in-memory value and the two representations are not structurally equal.
 */
data class Appointment(
    @PK val id: Int = 0,
    val description: String,
    val scheduledAt: LocalDateTime,
) : Entity<Int>
