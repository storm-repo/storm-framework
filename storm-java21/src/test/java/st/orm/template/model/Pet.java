package st.orm.template.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;

public record Pet(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @Persist(updatable = false) PetType type,
        @Nullable @FK Owner owner
) implements Entity<Integer> {}
