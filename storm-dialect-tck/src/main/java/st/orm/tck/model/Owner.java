package st.orm.tck.model;

import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Version;

@Builder(toBuilder = true)
public record Owner(
        @PK Integer id,
        String firstName,
        String lastName,
        Address address,
        @Nullable String telephone,
        @Version int version
) implements Entity<Integer> {}
