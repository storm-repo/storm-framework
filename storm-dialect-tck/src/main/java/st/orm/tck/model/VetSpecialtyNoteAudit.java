package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** The referenced key chain is two levels deep, over a compound key. */
@Builder(toBuilder = true)
@DbTable("vet_specialty_note_audit")
public record VetSpecialtyNoteAudit(
        @PK(generation = NONE) @FK VetSpecialtyNote note,
        String remark
) implements Entity<VetSpecialtyNote> {}
