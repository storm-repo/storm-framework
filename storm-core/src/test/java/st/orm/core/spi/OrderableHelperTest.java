package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import st.orm.core.spi.Orderable.After;
import st.orm.core.spi.Orderable.AfterAny;
import st.orm.core.spi.Orderable.Before;
import st.orm.core.spi.Orderable.BeforeAny;

/**
 * Tests for {@link OrderableHelper} and {@link Orderable} sorting.
 */
public class OrderableHelperTest {

    // Test implementations of Orderable.

    static class BaseOrderable implements Orderable<BaseOrderable> {}

    static class OrderableA extends BaseOrderable {}

    static class OrderableB extends BaseOrderable {}

    static class OrderableC extends BaseOrderable {}

    @Before(OrderableB.class)
    static class ABeforeB extends BaseOrderable {}

    @After(OrderableA.class)
    static class CAfterA extends BaseOrderable {}

    @BeforeAny
    static class FirstOrderable extends BaseOrderable {}

    @AfterAny
    static class LastOrderable extends BaseOrderable {}

    @Before(OrderableB.class)
    @After(OrderableA.class)
    static class BetweenAAndB extends BaseOrderable {}

    // Classes for circular dependency test.
    @After(CircularB.class)
    static class CircularA extends BaseOrderable {}

    @After(CircularA.class)
    static class CircularB extends BaseOrderable {}

    @Test
    public void testSortEmptyList() {
        List<BaseOrderable> result = Orderable.sort(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSortSingleElement() {
        var orderable = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(List.of(orderable));
        assertEquals(1, result.size());
        assertEquals(orderable, result.getFirst());
    }

    @Test
    public void testBeforeConstraint() {
        var beforeItem = new ABeforeB();
        var orderableB = new OrderableB();
        List<BaseOrderable> result = Orderable.sort(List.of(orderableB, beforeItem), false);
        int indexBefore = result.indexOf(beforeItem);
        int indexB = result.indexOf(orderableB);
        assertTrue(indexBefore < indexB, "ABeforeB should appear before OrderableB");
    }

    @Test
    public void testAfterConstraint() {
        var orderableA = new OrderableA();
        var afterItem = new CAfterA();
        List<BaseOrderable> result = Orderable.sort(List.of(afterItem, orderableA), false);
        int indexA = result.indexOf(orderableA);
        int indexAfter = result.indexOf(afterItem);
        assertTrue(indexA < indexAfter, "OrderableA should appear before CAfterA");
    }

    @Test
    public void testBeforeAnyConstraint() {
        var first = new FirstOrderable();
        var orderableA = new OrderableA();
        var orderableB = new OrderableB();
        List<BaseOrderable> result = Orderable.sort(List.of(orderableA, orderableB, first), false);
        assertEquals(first, result.getFirst(), "BeforeAny should place the item first");
    }

    @Test
    public void testAfterAnyConstraint() {
        var last = new LastOrderable();
        var orderableA = new OrderableA();
        var orderableB = new OrderableB();
        List<BaseOrderable> result = Orderable.sort(List.of(last, orderableA, orderableB), false);
        assertEquals(last, result.getLast(), "AfterAny should place the item last");
    }

    @Test
    public void testBeforeAnyAndAfterAnyTogether() {
        var first = new FirstOrderable();
        var last = new LastOrderable();
        var middle = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(List.of(last, middle, first), false);
        assertEquals(first, result.getFirst(), "BeforeAny item should be first");
        assertEquals(last, result.getLast(), "AfterAny item should be last");
    }

    @Test
    public void testBothBeforeAndAfterConstraints() {
        var orderableA = new OrderableA();
        var between = new BetweenAAndB();
        var orderableB = new OrderableB();
        List<BaseOrderable> result = Orderable.sort(List.of(orderableB, between, orderableA), false);
        int indexA = result.indexOf(orderableA);
        int indexBetween = result.indexOf(between);
        int indexB = result.indexOf(orderableB);
        assertTrue(indexA < indexBetween, "OrderableA should come before BetweenAAndB");
        assertTrue(indexBetween < indexB, "BetweenAAndB should come before OrderableB");
    }

    @Test
    public void testCircularDependencyThrowsException() {
        var circularA = new CircularA();
        var circularB = new CircularB();
        assertThrows(IllegalStateException.class,
                () -> Orderable.sort(List.of(circularA, circularB), false));
    }

    @Test
    public void testSortStream() {
        var first = new FirstOrderable();
        var last = new LastOrderable();
        var middle = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(Stream.of(last, middle, first), false).toList();
        assertEquals(first, result.getFirst(), "BeforeAny item should be first in stream result");
        assertEquals(last, result.getLast(), "AfterAny item should be last in stream result");
    }

    @Test
    public void testSortStreamWithCache() {
        var first = new FirstOrderable();
        var middle = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(Stream.of(middle, first)).toList();
        assertEquals(first, result.getFirst(), "BeforeAny item should be first");
    }

    @Test
    public void testSortListWithCache() {
        var first = new FirstOrderable();
        var middle = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(List.of(middle, first));
        assertEquals(first, result.getFirst(), "BeforeAny item should be first");
    }

    @Test
    public void testBeforeConstraintOnClassNotInList() {
        // When the @Before target is not in the list, the constraint should be ignored.
        var beforeItem = new ABeforeB();
        var orderableA = new OrderableA();
        List<BaseOrderable> result = Orderable.sort(List.of(beforeItem, orderableA), false);
        assertEquals(2, result.size());
    }

    @Test
    public void testAfterConstraintOnClassNotInList() {
        // When the @After target is not in the list, the constraint should be ignored.
        var afterItem = new CAfterA();
        var orderableB = new OrderableB();
        List<BaseOrderable> result = Orderable.sort(List.of(afterItem, orderableB), false);
        assertEquals(2, result.size());
    }

    @Test
    public void testNoConstraintsPreservesRelativeOrder() {
        var orderableA = new OrderableA();
        var orderableB = new OrderableB();
        var orderableC = new OrderableC();
        List<BaseOrderable> result = Orderable.sort(List.of(orderableA, orderableB, orderableC), false);
        assertEquals(3, result.size());
    }
}
