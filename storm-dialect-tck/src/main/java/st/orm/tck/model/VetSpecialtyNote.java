package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** The key is a compound foreign key spanning two columns. */
@Builder(toBuilder = true)
@DbTable("vet_specialty_note")
public record VetSpecialtyNote(
        @PK(generation = NONE) @FK VetSpecialty vetSpecialty,
        String note
) implements Entity<VetSpecialty> {}
