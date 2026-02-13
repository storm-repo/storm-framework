package st.orm.core.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

import static st.orm.GenerationStrategy.NONE;

/**
 * Extension entity for a pet, where the primary key is itself a foreign key reference to {@link Pet}.
 *
 * <p>This models the pattern where an entity's PK is an entity-typed FK (e.g., a one-to-one extension table
 * whose primary key is also a foreign key to the base entity).</p>
 */
@Builder(toBuilder = true)
public record PetExtension(
        @Nonnull @PK(generation = NONE) @FK("pet_id") Pet pet,
        @Nullable String notes
) implements Entity<Pet> {}
