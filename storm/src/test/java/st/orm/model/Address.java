package st.orm.model;

import lombok.Builder;

/**
 * Simple business object representing an address.
 */
@Builder(toBuilder = true)
public record Address(
        String address,
        String city
) {}
