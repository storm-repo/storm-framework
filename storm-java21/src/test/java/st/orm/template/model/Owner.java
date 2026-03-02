package st.orm.template.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Version;

public record Owner(
        @PK Integer id,
        @Nonnull String firstName,
        @Nonnull String lastName,
        @Nonnull Address address,
        @Nullable String telephone,
        @Version int version
) implements Person, Entity<Integer> {}
