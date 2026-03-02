package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.core.template.TemplateString;

/**
 * Tests for {@link StringTemplates}.
 */
public class StringTemplatesTest {

    @Test
    public void testFlattenNoNesting() {
        // Template with no nested TemplateString values.
        TemplateString template = TemplateString.of(
                List.of("SELECT * FROM users WHERE id = ", ""),
                List.of(42)
        );
        TemplateString flattened = StringTemplates.flatten(template);
        assertEquals(List.of("SELECT * FROM users WHERE id = ", ""), flattened.fragments());
        assertEquals(List.of(42), flattened.values());
    }

    @Test
    public void testFlattenWithNestedTemplate() {
        // Inner template: "status = ?" with value "active".
        TemplateString inner = TemplateString.of(
                List.of("status = ", ""),
                List.of("active")
        );
        // Outer template: "SELECT * FROM users WHERE [inner] ORDER BY id".
        TemplateString outer = TemplateString.of(
                List.of("SELECT * FROM users WHERE ", " ORDER BY id"),
                List.of(inner)
        );
        TemplateString flattened = StringTemplates.flatten(outer);
        assertEquals(List.of("SELECT * FROM users WHERE status = ", " ORDER BY id"), flattened.fragments());
        assertEquals(List.of("active"), flattened.values());
    }

    @Test
    public void testFlattenWithDeeplyNestedTemplate() {
        // Deep: "x = ?" with value 1.
        TemplateString deep = TemplateString.of(
                List.of("x = ", ""),
                List.of(1)
        );
        // Middle: "WHERE [deep] AND y = ?" with value 2.
        TemplateString middle = TemplateString.of(
                List.of("WHERE ", " AND y = ", ""),
                List.of(deep, 2)
        );
        // Outer: "SELECT * FROM t [middle]".
        TemplateString outer = TemplateString.of(
                List.of("SELECT * FROM t ", ""),
                List.of(middle)
        );
        TemplateString flattened = StringTemplates.flatten(outer);
        assertEquals(
                List.of("SELECT * FROM t WHERE x = ", " AND y = ", ""),
                flattened.fragments()
        );
        assertEquals(List.of(1, 2), flattened.values());
    }

    @Test
    public void testFlattenMixedNestedAndPlain() {
        TemplateString inner = TemplateString.of(
                List.of("a = ", ""),
                List.of("innerVal")
        );
        TemplateString outer = TemplateString.of(
                List.of("prefix ", " middle ", " suffix"),
                List.of(inner, "plainVal")
        );
        TemplateString flattened = StringTemplates.flatten(outer);
        assertEquals(List.of("prefix a = ", " middle ", " suffix"), flattened.fragments());
        assertEquals(List.of("innerVal", "plainVal"), flattened.values());
    }

    @Test
    public void testFlattenNoValues() {
        TemplateString template = TemplateString.of("SELECT 1");
        TemplateString flattened = StringTemplates.flatten(template);
        assertEquals(List.of("SELECT 1"), flattened.fragments());
        assertEquals(List.of(), flattened.values());
    }

    @Test
    public void testFlattenMultipleNestedTemplates() {
        TemplateString inner1 = TemplateString.of(
                List.of("a = ", ""),
                List.of(1)
        );
        TemplateString inner2 = TemplateString.of(
                List.of("b = ", ""),
                List.of(2)
        );
        TemplateString outer = TemplateString.of(
                List.of("WHERE ", " AND ", ""),
                List.of(inner1, inner2)
        );
        TemplateString flattened = StringTemplates.flatten(outer);
        assertEquals(List.of("WHERE a = ", " AND b = ", ""), flattened.fragments());
        assertEquals(List.of(1, 2), flattened.values());
    }

    @Test
    public void testFlattenNestedTemplateWithMultipleValues() {
        TemplateString inner = TemplateString.of(
                List.of("x = ", " OR y = ", ""),
                List.of(10, 20)
        );
        TemplateString outer = TemplateString.of(
                List.of("SELECT * WHERE ", ""),
                List.of(inner)
        );
        TemplateString flattened = StringTemplates.flatten(outer);
        assertEquals(List.of("SELECT * WHERE x = ", " OR y = ", ""), flattened.fragments());
        assertEquals(List.of(10, 20), flattened.values());
    }
}
