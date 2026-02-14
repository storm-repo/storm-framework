package st.orm.spring.model

import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Version
import java.time.Instant
import java.time.LocalDate

/**
 * Simple domain object representing a visit.
 */
@JvmRecord
data class Visit(
    @PK val id: Int = 0,
    val visitDate: LocalDate,
    val description: String? = null,
    @FK val pet: Pet,
    @Version val timestamp: Instant?,
) : Entity<Int>
