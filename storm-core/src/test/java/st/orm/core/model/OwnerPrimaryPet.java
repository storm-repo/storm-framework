package st.orm.core.model;

import static st.orm.GenerationStrategy.NONE;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * Junction-style entity whose primary key is an entity-typed foreign key to {@link Owner}, while its second
 * foreign key reaches the owner table again through {@code pet.owner}.
 *
 * <p>This models the pattern where the root's join graph contains the primary-key entity's table more than once
 * (e.g., a user-to-primary-session junction where the session also references the user). Id-based operations on
 * this entity must resolve against the primary-key path; type-based resolution is ambiguous.</p>
 */
@Builder(toBuilder = true)
public record OwnerPrimaryPet(
        @Nonnull @PK(generation = NONE) @FK("owner_id") Owner owner,
        @Nonnull @FK("pet_id") Pet pet
) implements Entity<Owner> {}
