package st.orm.spring.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Version;

import java.time.Instant;
import java.time.LocalDate;

public record Visit(
        @PK Integer id,
        @Nonnull LocalDate visitDate,
        @Nullable String description,
        @Nonnull @FK Pet pet,
        @Version Instant timestamp
) implements Entity<Integer>  {}
