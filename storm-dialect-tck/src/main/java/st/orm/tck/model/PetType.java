package st.orm.tck.model;

import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.PK;

@Builder(toBuilder = true)
public record PetType(
        @PK Integer id,
        String name,
        @Nullable String description
) implements Entity<Integer> {}
