package st.orm.template.model;

import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Version;

@DbTable("owner_view")
public record OwnerView(
        @PK Integer id,
        String firstName,
        String lastName,
        Address address,
        @Nullable String telephone,
        @Version int version
) implements Projection<Integer> {}
