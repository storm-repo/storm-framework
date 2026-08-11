package st.orm.core.model;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/** A user living in a country. Mapped to app_user because user is a reserved word in several databases. */
@DbTable("app_user")
public record User(
        @PK Integer id,
        String name,
        @FK Country country
) implements Entity<Integer> {}
