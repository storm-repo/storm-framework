package st.orm.model;

import st.orm.Name;
import lombok.Builder;

@Builder(toBuilder = true)
public record VetSpecialtyPK(
        @Name("vet_id") int vetId,
        @Name("specialty_id") int specialtyId
) {}
