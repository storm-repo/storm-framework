package st.orm.tck.model;

import static st.orm.GenerationStrategy.SEQUENCE;

import java.time.LocalDate;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** Sequence-generated key, naming the sequence the schema defines. */
@Builder(toBuilder = true)
@DbTable("pet")
public record Pet(
        @PK(generation = SEQUENCE, sequence = "pet_id_seq") Integer id,
        String name,
        LocalDate birthDate,
        @FK PetType type,
        @Nullable @FK Owner owner
) implements Entity<Integer> {}
