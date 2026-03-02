package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TemplateString}.
 */
public class TemplateStringTest {

    @Test
    public void testOfString() {
        TemplateString template = TemplateString.of("SELECT 1");
        assertEquals(List.of("SELECT 1"), template.fragments());
        assertTrue(template.values().isEmpty());
    }

    @Test
    public void testOfFragmentsAndValues() {
        TemplateString template = TemplateString.of(
                List.of("SELECT * WHERE id = ", ""),
                List.of(42)
        );
        assertEquals(List.of("SELECT * WHERE id = ", ""), template.fragments());
        assertEquals(List.of(42), template.values());
    }

    @Test
    public void testWrap() {
        TemplateString wrapped = TemplateString.wrap("value");
        assertEquals(List.of("", ""), wrapped.fragments());
        assertEquals(1, wrapped.values().size());
        assertEquals("value", wrapped.values().getFirst());
    }

    @Test
    public void testWrapNull() {
        TemplateString wrapped = TemplateString.wrap(null);
        assertEquals(List.of("", ""), wrapped.fragments());
        assertEquals(1, wrapped.values().size());
    }

    @Test
    public void testEmpty() {
        TemplateString empty = TemplateString.EMPTY;
        assertEquals(List.of(""), empty.fragments());
        assertTrue(empty.values().isEmpty());
    }

    @Test
    public void testCombineEmpty() {
        TemplateString combined = TemplateString.combine();
        assertSame(TemplateString.EMPTY, combined);
    }

    @Test
    public void testCombineSingle() {
        TemplateString single = TemplateString.of("SELECT 1");
        TemplateString combined = TemplateString.combine(single);
        assertSame(single, combined);
    }

    @Test
    public void testCombineMultiple() {
        TemplateString first = TemplateString.of(
                List.of("SELECT * FROM ", ""),
                List.of("users")
        );
        TemplateString second = TemplateString.of(
                List.of(" WHERE id = ", ""),
                List.of(42)
        );
        TemplateString combined = TemplateString.combine(first, second);
        assertEquals(3, combined.fragments().size());
        assertEquals(2, combined.values().size());
        assertEquals("users", combined.values().get(0));
        assertEquals(42, combined.values().get(1));
    }

    @Test
    public void testCombineList() {
        TemplateString first = TemplateString.of("SELECT 1");
        TemplateString second = TemplateString.of(" UNION SELECT 2");
        TemplateString combined = TemplateString.combine(List.of(first, second));
        assertEquals(1, combined.fragments().size());
        assertEquals("SELECT 1 UNION SELECT 2", combined.fragments().getFirst());
    }

    @Test
    public void testConstructorValidation() {
        // Fragments must have exactly one more element than values.
        assertThrows(IllegalArgumentException.class, () -> new TemplateString(
                List.of("a", "b", "c"),
                List.of(1)
        ));
    }

    @Test
    public void testConstructorWithArrays() {
        TemplateString template = new TemplateString(
                new String[]{"SELECT ", " FROM ", ""},
                new Object[]{"*", "users"}
        );
        assertEquals(List.of("SELECT ", " FROM ", ""), template.fragments());
        assertEquals(2, template.values().size());
    }

    @Test
    public void testCombineNullThrows() {
        assertThrows(NullPointerException.class, () -> TemplateString.combine((TemplateString[]) null));
    }

    @Test
    public void testCombineWithNullElementThrows() {
        assertThrows(NullPointerException.class, () -> TemplateString.combine(
                TemplateString.of("a"), null
        ));
    }

    @Test
    public void testCombineMultipleWithValues() {
        TemplateString first = TemplateString.of(
                List.of("a = ", ""),
                List.of(1)
        );
        TemplateString second = TemplateString.of(
                List.of(" AND b = ", ""),
                List.of(2)
        );
        TemplateString third = TemplateString.of(
                List.of(" AND c = ", ""),
                List.of(3)
        );
        TemplateString combined = TemplateString.combine(first, second, third);
        assertEquals(4, combined.fragments().size());
        assertEquals(3, combined.values().size());
        assertEquals(List.of(1, 2, 3), combined.values());
    }
}
