package st.orm.core.model;

import static st.orm.GenerationStrategy.NONE;

import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * Junction-style entity whose primary key is an entity-typed foreign key to {@link Appointment}. Because the
 * appointment carries a column that does not round-trip bit-exact, correlating this entity by its raw id value
 * requires more than the database key; operations on it must correlate by the key column instead.
 */
public record AppointmentReport(
        @PK(generation = NONE) @FK("appointment_id") Appointment appointment,
        String report
) implements Entity<Appointment> {}
