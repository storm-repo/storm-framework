package st.orm.tck.model;

import static st.orm.GenerationStrategy.NONE;

import lombok.Builder;
import st.orm.Entity;
import st.orm.PK;

@Builder(toBuilder = true)
public record Specialty(
        @PK(generation = NONE) Integer id,
        String name
) implements Entity<Integer> {}
