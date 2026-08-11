package st.orm.core.model;

import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.FK;

/**
 * Simple business object representing an address.
 */
@Builder(toBuilder = true)
public record Address(
        String address,
        @Nullable @FK City city
) {}
