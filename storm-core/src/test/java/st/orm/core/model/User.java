package st.orm.core.model;

import jakarta.annotation.Nonnull;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** A user living in a country. Mapped to app_user because user is a reserved word in several databases. */
@DbTable("app_user")
public record User(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @FK Country country
) implements Entity<Integer> {}
