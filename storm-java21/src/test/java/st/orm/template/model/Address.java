package st.orm.template.model;

import st.orm.FK;

public record Address(
        String address,
        @FK City city
) {}
