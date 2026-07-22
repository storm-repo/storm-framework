package st.orm.core.model;

import jakarta.annotation.Nonnull;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * Child of {@link AppointmentReport}, referencing the junction row through its entity-typed primary key.
 */
public record AppointmentReportReview(
        @PK Integer id,
        @Nonnull @FK("appointment_report_id") AppointmentReport appointmentReport,
        @Nonnull String review
) implements Entity<Integer> {}
