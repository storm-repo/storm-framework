package st.orm.template.model;

import jakarta.annotation.Nonnull;
import st.orm.Entity;
import st.orm.PK;

public record City(
        @PK Integer id,
        @Nonnull String name
) implements Entity<Integer> {}
