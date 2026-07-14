package st.orm.core.model;

import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.FK;

/**
 * Simple business object representing an address.
 */
@Builder(toBuilder = true)
public record Address(
        String address,
        @Nullable @FK City city
) {}
