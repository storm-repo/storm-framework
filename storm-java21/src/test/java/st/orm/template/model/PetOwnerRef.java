package st.orm.template.model;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDate;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Ref;

/**
 * Maps the pet table with the owner declared as a reference, so the owner is selected as its foreign key column
 * rather than joined into every read. {@link Pet} maps the same table with the owner as an entity, which gives an
 * entity-graph baseline to compare a resolved reference against.
 */
@DbTable("pet")
public record PetOwnerRef(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @Persist(updatable = false) LocalDate birthDate,
        @Nonnull @FK @DbColumn("type_id") @Persist(updatable = false) PetType type,
        @Nullable @FK @DbColumn("owner_id") Ref<Owner> owner
) implements Entity<Integer> {}
