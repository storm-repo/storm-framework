package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** The referenced key chain is two levels deep, over a single column. */
@Builder(toBuilder = true)
@DbTable("specialty_note_history")
public record SpecialtyNoteHistory(
        @PK(generation = NONE) @FK SpecialtyNote note,
        String remark
) implements Entity<SpecialtyNote> {}
