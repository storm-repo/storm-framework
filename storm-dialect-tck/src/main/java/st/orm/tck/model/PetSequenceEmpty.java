package st.orm.tck.model;

import static st.orm.GenerationStrategy.SEQUENCE;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** The same table as {@link Pet}, but asking for a sequence without naming one, which a dialect must refuse. */
@Builder(toBuilder = true)
@DbTable("pet")
public record PetSequenceEmpty(
        @PK(generation = SEQUENCE) Integer id,
        String name,
        LocalDate birthDate,
        @FK PetType type,
        @Nullable @FK Owner owner
) implements Entity<Integer> {}
