package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import java.time.Instant;
import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** Dependent one-to-one: the key is the foreign key, and the row carries a temporal column alongside it. */
@Builder(toBuilder = true)
@DbTable("specialty_note")
public record SpecialtyNote(
        @PK(generation = NONE) @FK Specialty specialty,
        String note,
        Instant updatedAt
) implements Entity<Specialty> {}
