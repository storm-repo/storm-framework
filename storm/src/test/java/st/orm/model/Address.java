package st.orm.model;

import lombok.Builder;
import st.orm.FK;

/**
 * Simple business object representing an address.
 */
@Builder(toBuilder = true)
public record Address(
        String address,
        @FK City city
) {}
