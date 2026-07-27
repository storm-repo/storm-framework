package st.orm.core.model;

import jakarta.annotation.Nonnull;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Ref;

/**
 * The city that is a country's capital, holding the country it lies in as a reference. Together with
 * {@link LargestCity} it gives the country two separate ways back to itself.
 */
@DbTable("country_city")
public record CapitalCity(
        @PK Integer id,
        @Nonnull String name,
        @Nonnull @FK Ref<Country> country
) implements Entity<Integer> {}
