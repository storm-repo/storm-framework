package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SliceTest {

    @Test
    void sliceWithContent() {
        Slice<String> slice = new Slice<>(List.of("a", "b", "c"), true);
        assertEquals(3, slice.content().size());
        assertEquals("a", slice.content().get(0));
        assertTrue(slice.hasNext());
    }

    @Test
    void sliceWithEmptyContent() {
        Slice<String> slice = new Slice<>(List.of(), false);
        assertTrue(slice.content().isEmpty());
        assertFalse(slice.hasNext());
    }

    @Test
    void sliceContentIsImmutableCopy() {
        List<String> original = new ArrayList<>(List.of("a", "b"));
        Slice<String> slice = new Slice<>(original, false);
        original.add("c");
        assertEquals(2, slice.content().size());
    }

    @Test
    void sliceContentIsUnmodifiable() {
        Slice<String> slice = new Slice<>(List.of("a"), false);
        assertThrows(UnsupportedOperationException.class, () -> slice.content().add("b"));
    }

    @Test
    void sliceHasNextTrue() {
        Slice<Integer> slice = new Slice<>(List.of(1), true);
        assertTrue(slice.hasNext());
    }

    @Test
    void sliceHasNextFalse() {
        Slice<Integer> slice = new Slice<>(List.of(1), false);
        assertFalse(slice.hasNext());
    }
}
