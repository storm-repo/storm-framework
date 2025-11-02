package st.orm.jackson.model;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;

import static st.orm.GenerationStrategy.NONE;

@Builder(toBuilder = true)
public record VetSpecialty(
        @Nonnull @PK(generation = NONE) VetSpecialtyPK id,  // Implicitly @Inlined
        @Nonnull @FK @Persist(insertable = false) Vet vet,
        @Nonnull @FK @Persist(insertable = false) Specialty specialty) implements Entity<VetSpecialtyPK> {
    public VetSpecialty(@Nonnull VetSpecialtyPK pk) {
        //noinspection DataFlowIssue
        this(pk, null, null);
    }
}
