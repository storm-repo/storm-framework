package st.orm.template.model;

import st.orm.Entity;
import st.orm.PK;

public record PetType(
        @PK Integer id,
        String name
) implements Entity<Integer> {}
