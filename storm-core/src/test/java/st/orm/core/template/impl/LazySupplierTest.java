package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LazySupplier}.
 */
public class LazySupplierTest {

    @Test
    public void testLazyInitialization() {
        AtomicInteger callCount = new AtomicInteger(0);
        LazySupplier<String> lazy = new LazySupplier<>(() -> {
            callCount.incrementAndGet();
            return "hello";
        });

        assertEquals(0, callCount.get(), "Supplier should not be called until get()");
        assertEquals("hello", lazy.get());
        assertEquals(1, callCount.get(), "Supplier should be called exactly once");
        assertEquals("hello", lazy.get());
        assertEquals(1, callCount.get(), "Subsequent get() should not invoke supplier again");
    }

    @Test
    public void testLazyStaticFactoryMethod() {
        AtomicInteger callCount = new AtomicInteger(0);
        var lazy = LazySupplier.lazy(() -> {
            callCount.incrementAndGet();
            return 42;
        });

        assertEquals(42, lazy.get());
        assertEquals(1, callCount.get());
        assertEquals(42, lazy.get());
        assertEquals(1, callCount.get(), "Static factory lazy supplier should also only invoke once");
    }

    @Test
    public void testConstructorWithInitialValue() {
        AtomicInteger callCount = new AtomicInteger(0);
        LazySupplier<String> lazy = new LazySupplier<>(() -> {
            callCount.incrementAndGet();
            return "fallback";
        }, "initial");

        assertEquals("initial", lazy.get());
        assertEquals(0, callCount.get(), "Supplier should not be called when initial value is provided");
    }

    @Test
    public void testValueReturnsEmptyBeforeGet() {
        LazySupplier<String> lazy = new LazySupplier<>(() -> "hello");
        assertTrue(lazy.value().isEmpty(), "Value should be empty before get() is called");
    }

    @Test
    public void testValueReturnsPresentAfterGet() {
        LazySupplier<String> lazy = new LazySupplier<>(() -> "hello");
        lazy.get();
        assertTrue(lazy.value().isPresent(), "Value should be present after get()");
        assertEquals("hello", lazy.value().get());
    }

    @Test
    public void testValueReturnsPresentWithInitialValue() {
        LazySupplier<String> lazy = new LazySupplier<>(() -> "fallback", "initial");
        assertTrue(lazy.value().isPresent(), "Value should be present when initial value is provided");
        assertEquals("initial", lazy.value().get());
    }

    @Test
    public void testRemoveClearsLazyValue() {
        LazySupplier<String> lazy = new LazySupplier<>(() -> "hello");
        lazy.get();
        assertTrue(lazy.value().isPresent());

        lazy.remove();
        assertTrue(lazy.value().isEmpty(), "Value should be empty after remove()");
    }

    @Test
    public void testGetAfterRemoveReinvokesSupplier() {
        AtomicInteger callCount = new AtomicInteger(0);
        LazySupplier<String> lazy = new LazySupplier<>(() -> {
            callCount.incrementAndGet();
            return "hello";
        });

        lazy.get();
        assertEquals(1, callCount.get());

        lazy.remove();
        lazy.get();
        assertEquals(2, callCount.get(), "After remove, get should invoke supplier again");
    }

    @Test
    public void testRequireNonNullElseGet() {
        assertEquals("existing", LazySupplier.requireNonNullElseGet("existing", () -> "fallback"));
        assertEquals("fallback", LazySupplier.requireNonNullElseGet(null, () -> "fallback"));
    }

    @Test
    public void testRequireNonNullElseGetThrowsOnNullSupplier() {
        assertThrows(NullPointerException.class, () -> LazySupplier.requireNonNullElseGet(null, null));
    }

    @Test
    public void testRequireNonNullElseGetThrowsWhenSupplierReturnsNull() {
        assertThrows(NullPointerException.class, () -> LazySupplier.requireNonNullElseGet(null, () -> null));
    }
}
