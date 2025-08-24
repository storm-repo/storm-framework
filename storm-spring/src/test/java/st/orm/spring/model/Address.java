package st.orm.spring.model;

import st.orm.FK;

/**
 * Simple business object representing an address.
 */
public record Address(
        String address,
        @FK City city
) {}
