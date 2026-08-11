package st.orm.core.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;

@Builder(toBuilder = true)
public record VetSpecialty(
        @PK(generation = NONE) VetSpecialtyPK id,  // Implicitly @Inlined
        @Persist(insertable = false) @FK Vet vet,
        @Persist(insertable = false) @FK Specialty specialty) implements Entity<VetSpecialtyPK> {
    public VetSpecialty(VetSpecialtyPK pk) {
        //noinspection DataFlowIssue
        this(pk, null, null);
    }
}
