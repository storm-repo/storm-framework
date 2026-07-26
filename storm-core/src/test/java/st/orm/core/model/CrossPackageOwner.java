package st.orm.core.model;

import jakarta.annotation.Nullable;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.model.crosspackage.CrossPackageDetails;

/**
 * Reached through a reference, so its own cross-package inline record is expanded into navigation metamodels.
 */
@DbTable("cross_package_owner")
public record CrossPackageOwner(
        @PK Integer id,
        @Nullable CrossPackageDetails details
) implements Entity<Integer> {}
