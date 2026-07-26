package st.orm.core.model;

import jakarta.annotation.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;
import st.orm.core.model.crosspackage.CrossPackageDetails;

/**
 * Embeds an inline record from another package, and references an entity that does the same, so the generated
 * navigation metamodels have to qualify those types.
 */
@DbTable("cross_package_holder")
public record CrossPackageHolder(
        @PK Integer id,
        @Nullable CrossPackageDetails details,
        @Nullable @FK Ref<CrossPackageOwner> owner
) implements Entity<Integer> {}
