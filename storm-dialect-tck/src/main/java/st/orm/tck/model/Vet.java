package st.orm.tck.model;

import lombok.Builder;
import st.orm.Entity;
import st.orm.PK;

@Builder(toBuilder = true)
public record Vet(
        @PK Integer id,
        String firstName,
        String lastName
) implements Entity<Integer> {}
