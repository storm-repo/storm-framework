package st.orm.core.model;

import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

/** The most populous city of a country, holding the country it lies in as a reference. */
@DbTable("country_city")
public record LargestCity(
        @PK Integer id,
        String name,
        @FK Ref<Country> country
) implements Entity<Integer> {}
