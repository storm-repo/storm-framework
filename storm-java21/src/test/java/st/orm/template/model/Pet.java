package st.orm.template.model;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;

public record Pet(
        @PK Integer id,
        String name,
        @Persist(updatable = false) LocalDate birthDate,
        @FK @Persist(updatable = false) PetType type,
        @Nullable @FK Owner owner
) implements Entity<Integer> {}
