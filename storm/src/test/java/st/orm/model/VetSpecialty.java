package st.orm.model;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import st.orm.repository.Entity;
import st.orm.FK;
import st.orm.Name;
import st.orm.PK;
import st.orm.Persist;

@Builder(toBuilder = true)
@Name("vet_specialty")
public record VetSpecialty(
        @Nonnull @PK(autoGenerated = false) VetSpecialtyPK id,  // Implicitly @Inlined
        @Nonnull @Persist(insertable = false) @FK @Name("vet_id") Vet vet,
        @Nonnull @Persist(insertable = false) @FK @Name("specialty_id") Specialty specialty) implements Entity<VetSpecialtyPK> {
    public VetSpecialty(@Nonnull VetSpecialtyPK pk) {
        //noinspection DataFlowIssue
        this(pk, null, null);
    }
}
