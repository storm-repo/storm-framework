package st.orm.template.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.DbTable;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Version;

@DbTable("owner_view")
public record OwnerView(
        @PK Integer id,
        @Nonnull String firstName,
        @Nonnull String lastName,
        @Nonnull Address address,
        @Nullable String telephone,
        @Version int version
) implements Projection<Integer> {}
