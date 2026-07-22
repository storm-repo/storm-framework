package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Ref;

/**
 * Tests for ref equality across row-identity normalization: refs describing the same database row compare equal
 * regardless of non-key divergence in an entity-typed id.
 */
public class RefEqualityTest {

    record Town(@PK Integer id, String name) implements Entity<Integer> {}

    record TownBadge(@PK Town town, String label) implements Entity<Town> {}

    @Test
    public void testScalarKeyedRefsCompareById() {
        assertEquals(Ref.of(Town.class, 5), Ref.of(Town.class, 5));
        assertEquals(Ref.of(Town.class, 5).hashCode(), Ref.of(Town.class, 5).hashCode());
        assertNotEquals(Ref.of(Town.class, 5), Ref.of(Town.class, 6));
    }

    @Test
    public void testDetachedIdRefEqualsWrappedEntityRef() {
        Ref<Town> byId = Ref.of(Town.class, 5);
        Ref<Town> wrapped = Ref.of(new Town(5, "Sun Paririe"));
        assertEquals(byId, wrapped);
        assertEquals(wrapped, byId);
        assertEquals(byId.hashCode(), wrapped.hashCode());
    }

    @Test
    public void testEntityTypedKeyRefsCompareByRowIdentity() {
        // The key entities diverge in a non-key column only; both refs describe the same row.
        Ref<TownBadge> first = Ref.of(TownBadge.class, new Town(5, "Sun Paririe"));
        Ref<TownBadge> second = Ref.of(TownBadge.class, new Town(5, "Sun Prairie"));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testEntityTypedKeyWrappedRefsCompareByRowIdentity() {
        Ref<TownBadge> first = Ref.of(new TownBadge(new Town(5, "Sun Paririe"), "founders"));
        Ref<TownBadge> second = Ref.of(TownBadge.class, new Town(5, "Sun Prairie"));
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testDifferentRowsCompareUnequal() {
        assertNotEquals(
                Ref.of(TownBadge.class, new Town(5, "Sun Paririe")),
                Ref.of(TownBadge.class, new Town(6, "Sun Paririe")));
    }

    @Test
    public void testDifferentTypesCompareUnequal() {
        assertNotEquals(Ref.of(Town.class, 5), Ref.of(TownBadge.class, new Town(5, "Sun Paririe")));
    }
}
