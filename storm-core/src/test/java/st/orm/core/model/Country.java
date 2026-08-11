package st.orm.core.model;

import org.jspecify.annotations.Nullable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * A country that is reachable from itself. Its capital and its largest city are ordinary foreign keys, so a read of a
 * country brings them along, and each of them refers back to the country it lies in. Resolving the country table by
 * type from a query that navigated to it therefore has to choose between the country itself and the two occurrences
 * reached by continuing around the cycle.
 */
public record Country(
        @PK Integer id,
        String name,
        @Nullable @FK CapitalCity capital,
        @Nullable @FK LargestCity largestCity
) implements Entity<Integer> {}
