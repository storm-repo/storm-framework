package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class ScrollableTest {

    @SuppressWarnings("unchecked")
    private static <T extends Data, E> Metamodel.Key<T, E> stubKey(Class<E> fieldType, String fieldPath) {
        return (Metamodel.Key<T, E>) Proxy.newProxyInstance(
                Metamodel.Key.class.getClassLoader(),
                new Class<?>[] { Metamodel.Key.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "isNullable" -> false;
                    case "fieldType" -> fieldType;
                    case "fieldPath" -> fieldPath;
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Data, S> Metamodel<T, S> stubSort(Class<S> fieldType, String fieldPath) {
        return (Metamodel<T, S>) Proxy.newProxyInstance(
                Metamodel.class.getClassLoader(),
                new Class<?>[] { Metamodel.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "fieldType" -> fieldType;
                    case "fieldPath" -> fieldPath;
                    default -> null;
                });
    }

    private static final Metamodel.Key<Data, Integer> KEY = stubKey(Integer.class, "id");
    private static final Metamodel<Data, String> SORT = stubSort(String.class, "name");

    // Factory methods

    @Test
    void ofCreatesForwardScrollableWithoutCursor() {
        var scrollable = Scrollable.of(KEY, 20);
        assertTrue(scrollable.isForward());
        assertFalse(scrollable.hasCursor());
        assertFalse(scrollable.isComposite());
        assertEquals(20, scrollable.size());
    }

    @Test
    void ofCompositeCreatesForwardScrollableWithSort() {
        var scrollable = Scrollable.of(KEY, SORT, 10);
        assertTrue(scrollable.isForward());
        assertFalse(scrollable.hasCursor());
        assertTrue(scrollable.isComposite());
        assertEquals(10, scrollable.size());
    }

    @Test
    void ofRejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> Scrollable.of(KEY, 0));
        assertThrows(IllegalArgumentException.class, () -> Scrollable.of(KEY, -1));
    }

    @Test
    void ofRejectsNullKey() {
        assertThrows(NullPointerException.class, () -> Scrollable.of(null, 10));
    }

    // Direction methods

    @Test
    void backwardReturnsBackwardScrollable() {
        var scrollable = Scrollable.of(KEY, 20).backward();
        assertFalse(scrollable.isForward());
    }

    @Test
    void backwardIsIdempotent() {
        var scrollable = Scrollable.of(KEY, 20).backward();
        assertSame(scrollable, scrollable.backward());
    }

    @Test
    void forwardReturnsForwardScrollable() {
        var scrollable = Scrollable.of(KEY, 20).backward().forward();
        assertTrue(scrollable.isForward());
    }

    @Test
    void forwardIsIdempotent() {
        var scrollable = Scrollable.of(KEY, 20);
        assertSame(scrollable, scrollable.forward());
    }

    @Test
    void reverseTogglesDirection() {
        var forward = Scrollable.of(KEY, 20);
        var backward = forward.reverse();
        assertFalse(backward.isForward());
        assertTrue(backward.reverse().isForward());
    }

    // Validation

    @Test
    void compositeSortCursorRequiredWhenKeyCursorPresent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Scrollable<>(KEY, 42, SORT, null, 20, true));
    }

    @Test
    void sortCursorWithoutSortFieldIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Scrollable<>(KEY, null, null, "value", 20, true));
    }
}
