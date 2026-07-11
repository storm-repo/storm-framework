package st.orm.spring.boot.test.domain;

import jakarta.annotation.Nullable;
import st.orm.Entity;
import st.orm.PK;

public record Visit(@PK Integer id, @Nullable String description) implements Entity<Integer> {
}
