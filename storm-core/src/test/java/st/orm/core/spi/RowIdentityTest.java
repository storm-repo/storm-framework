package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Ref;

/**
 * Tests for {@link RowIdentity}.
 */
public class RowIdentityTest {

    record Town(@PK Integer id, String name) implements Entity<Integer> {}

    record TownBadge(@PK Town town, String label) implements Entity<Town> {}

    record ScalarKey(int high, int low) {}

    record CompositeKey(Town town, int sequence) {}

    @Test
    public void testRequiresNormalizationDecidesPerClass() {
        assertEquals(false, RowIdentity.requiresNormalization(Integer.class));
        assertEquals(false, RowIdentity.requiresNormalization(String.class));
        assertEquals(false, RowIdentity.requiresNormalization(ScalarKey.class));
        assertEquals(true, RowIdentity.requiresNormalization(Town.class));
        assertEquals(true, RowIdentity.requiresNormalization(TownBadge.class));
        assertEquals(true, RowIdentity.requiresNormalization(CompositeKey.class));
    }

    @Test
    public void testScalarKeyPassesThroughUnchanged() {
        Integer id = 42;
        assertSame(id, RowIdentity.normalize(id));
    }

    @Test
    public void testScalarOnlyCompositeKeyPassesThroughUnchanged() {
        ScalarKey key = new ScalarKey(1, 2);
        assertSame(key, RowIdentity.normalize(key));
    }

    @Test
    public void testNullPassesThrough() {
        assertSame(null, RowIdentity.normalize(null));
    }

    @Test
    public void testEntityKeyReducesToItsPrimaryKey() {
        assertEquals(RowIdentity.normalize(7),
                RowIdentity.normalize(new Town(7, "Sun Paririe")));
    }

    @Test
    public void testEntityKeyChainReducesRecursively() {
        TownBadge badge = new TownBadge(new Town(7, "Sun Paririe"), "founders");
        assertEquals(RowIdentity.normalize(7), RowIdentity.normalize(badge));
    }

    @Test
    public void testRefKeyReducesToItsPrimaryKey() {
        assertEquals(RowIdentity.normalize(7), RowIdentity.normalize(Ref.of(Town.class, 7)));
    }

    @Test
    public void testDivergentRepresentationsOfSameRowNormalizeEqual() {
        // The two keys describe the same row; the towns diverge in a non-key column only.
        Object first = RowIdentity.normalize(new CompositeKey(new Town(7, "Sun Paririe"), 3));
        Object second = RowIdentity.normalize(new CompositeKey(new Town(7, "Sun Prairie"), 3));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testDifferentRowsNormalizeUnequal() {
        Object first = RowIdentity.normalize(new CompositeKey(new Town(7, "Sun Paririe"), 3));
        Object second = RowIdentity.normalize(new CompositeKey(new Town(8, "Sun Paririe"), 3));
        Object third = RowIdentity.normalize(new CompositeKey(new Town(7, "Sun Paririe"), 4));
        assertNotEquals(first, second);
        assertNotEquals(first, third);
    }
}
