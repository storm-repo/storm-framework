package st.orm.core.model;

import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * Child of {@link AppointmentReport}, referencing the junction row through its entity-typed primary key.
 */
public record AppointmentReportReview(
        @PK Integer id,
        @FK("appointment_report_id") AppointmentReport appointmentReport,
        String review
) implements Entity<Integer> {}
