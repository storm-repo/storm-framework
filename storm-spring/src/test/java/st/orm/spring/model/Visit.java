package st.orm.spring.model;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Version;

public record Visit(
        @PK Integer id,
        LocalDate visitDate,
        @Nullable String description,
        @FK Pet pet,
        @Version Instant timestamp
) implements Entity<Integer>  {}
