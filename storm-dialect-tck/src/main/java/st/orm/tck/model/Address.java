package st.orm.tck.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Address(
        String address,
        String city
) {}
