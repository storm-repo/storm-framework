package st.orm.jackson.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;

@Builder(toBuilder = true)
public record VetSpecialty(
        @PK(generation = NONE) VetSpecialtyPK id,  // Implicitly @Inlined
        @FK @Persist(insertable = false) Vet vet,
        @FK @Persist(insertable = false) Specialty specialty) implements Entity<VetSpecialtyPK> {
    public VetSpecialty(VetSpecialtyPK pk) {
        //noinspection DataFlowIssue
        this(pk, null, null);
    }
}
