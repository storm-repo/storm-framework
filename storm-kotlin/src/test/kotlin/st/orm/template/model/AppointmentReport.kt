package st.orm.template.model

import st.orm.Entity
import st.orm.FK
import st.orm.GenerationStrategy.NONE
import st.orm.PK

/**
 * Junction-style entity whose primary key is an entity-typed foreign key to [Appointment]. Because the appointment
 * carries a column that does not round-trip bit-exact, operations on this entity must correlate by the key column
 * rather than by structural equality of the key entity.
 */
data class AppointmentReport(
    @PK(generation = NONE) @FK("appointment_id") val appointment: Appointment,
    val report: String,
) : Entity<Appointment>
