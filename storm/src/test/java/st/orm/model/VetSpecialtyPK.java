package st.orm.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record VetSpecialtyPK(
        int vetId,
        int specialtyId
) {}
