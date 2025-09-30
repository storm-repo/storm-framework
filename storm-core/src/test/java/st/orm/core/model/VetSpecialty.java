package st.orm.core.model;

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
        @Nonnull @Persist(insertable = false) @FK Vet vet,
        @Nonnull @Persist(insertable = false) @FK Specialty specialty) implements Entity<VetSpecialtyPK> {
    public VetSpecialty(@Nonnull VetSpecialtyPK pk) {
        //noinspection DataFlowIssue
        this(pk, null, null);
    }
}
