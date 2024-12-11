package st.orm.json.model;

import lombok.Builder;
import st.orm.DbName;

@Builder(toBuilder = true)
public record VetSpecialtyPK(
        @DbName("vet_id") int vetId,
        @DbName("specialty_id") int specialtyId
) {}
