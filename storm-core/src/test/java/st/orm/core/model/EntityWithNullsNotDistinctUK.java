package st.orm.core.model;

import lombok.Builder;
import st.orm.Entity;
import st.orm.PK;
import st.orm.UK;

/**
 * Test entity with a @UK(nullsDistinct = false) inline record whose constituent fields are nullable.
 * When nullsDistinct is false, the compound key should NOT be considered effectively nullable
 * because the database constraint prevents duplicate NULLs.
 */
@Builder(toBuilder = true)
public record EntityWithNullsNotDistinctUK(
        @PK Integer id,
        @UK(nullsDistinct = false) NullableCompoundUK uniqueKey
) implements Entity<Integer> {
}
