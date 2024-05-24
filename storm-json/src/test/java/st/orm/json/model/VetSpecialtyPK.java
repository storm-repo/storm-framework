package st.orm.json.model;

import lombok.Builder;
import st.orm.Name;

@Builder(toBuilder = true)
public record VetSpecialtyPK(
        @Name("vet_id") int vetId,
        @Name("specialty_id") int specialtyId
) {}
