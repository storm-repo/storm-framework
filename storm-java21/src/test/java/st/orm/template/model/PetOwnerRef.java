package st.orm.template.model;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
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
        String name,
        @Persist(updatable = false) LocalDate birthDate,
        @FK @DbColumn("type_id") @Persist(updatable = false) PetType type,
        @Nullable @FK @DbColumn("owner_id") Ref<Owner> owner
) implements Entity<Integer> {}
