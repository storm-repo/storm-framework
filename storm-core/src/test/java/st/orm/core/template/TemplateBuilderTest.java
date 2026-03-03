package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TemplateBuilder}, covering the parseFragments method branches:
 * escaped null sequences (\\0), NUL delimiters, backslash at end of string,
 * backslash not followed by '0', and the create() methods.
 */
public class TemplateBuilderTest {

    @Test
    public void createWithLambdaShouldProduceCorrectFragmentsAndValues() {
        TemplateString result = TemplateBuilder.create(context ->
                "SELECT * FROM table WHERE id = " + context.insert(42));
        assertNotNull(result);
        assertEquals(2, result.fragments().size());
        assertEquals("SELECT * FROM table WHERE id = ", result.fragments().get(0));
        assertEquals("", result.fragments().get(1));
        assertEquals(1, result.values().size());
        assertEquals(42, result.values().get(0));
    }

    @Test
    public void createWithLambdaNoInsertionsShouldProduceSingleFragment() {
        TemplateString result = TemplateBuilder.create(context -> "SELECT 1");
        assertEquals(1, result.fragments().size());
        assertEquals("SELECT 1", result.fragments().get(0));
        assertTrue(result.values().isEmpty());
    }

    @Test
    public void createWithLambdaMultipleInsertionsShouldProduceCorrectFragments() {
        TemplateString result = TemplateBuilder.create(context ->
                "SELECT * FROM t WHERE a = " + context.insert(1) + " AND b = " + context.insert(2));
        assertEquals(3, result.fragments().size());
        assertEquals("SELECT * FROM t WHERE a = ", result.fragments().get(0));
        assertEquals(" AND b = ", result.fragments().get(1));
        assertEquals("", result.fragments().get(2));
        assertEquals(2, result.values().size());
    }

    @Test
    public void createWithRawTemplateAndValuesShouldParse() {
        // The create(String, Object...) method uses NUL (\0) as delimiter in the template.
        TemplateString result = TemplateBuilder.create("SELECT * FROM t WHERE id = \0", 42);
        assertEquals(2, result.fragments().size());
        assertEquals("SELECT * FROM t WHERE id = ", result.fragments().get(0));
        assertEquals("", result.fragments().get(1));
        assertEquals(1, result.values().size());
        assertEquals(42, result.values().get(0));
    }

    @Test
    public void createWithRawTemplateEscapedNullShouldPreserveAsNul() {
        // "\\0" in the raw template should be parsed as a literal NUL character in the fragment.
        TemplateString result = TemplateBuilder.create("before\\0after");
        assertEquals(1, result.fragments().size());
        assertEquals("before\0after", result.fragments().get(0));
    }

    @Test
    public void createWithRawTemplateBackslashAtEndShouldBePreserved() {
        // Backslash at the end of the string (no following char) should be treated as a regular character.
        TemplateString result = TemplateBuilder.create("trailing\\");
        assertEquals(1, result.fragments().size());
        assertEquals("trailing\\", result.fragments().get(0));
    }

    @Test
    public void createWithRawTemplateBackslashNotFollowedByZeroShouldBePreserved() {
        // Backslash followed by something other than '0' should be preserved as-is.
        TemplateString result = TemplateBuilder.create("path\\n\\t");
        assertEquals(1, result.fragments().size());
        assertEquals("path\\n\\t", result.fragments().get(0));
    }

    @Test
    public void createWithEmptyRawTemplateShouldProduceSingleEmptyFragment() {
        TemplateString result = TemplateBuilder.create("");
        assertEquals(1, result.fragments().size());
        assertEquals("", result.fragments().get(0));
        assertTrue(result.values().isEmpty());
    }

    @Test
    public void createWithRawTemplateOnlyNulDelimiterShouldProduceTwoEmptyFragments() {
        TemplateString result = TemplateBuilder.create("\0", "value");
        assertEquals(2, result.fragments().size());
        assertEquals("", result.fragments().get(0));
        assertEquals("", result.fragments().get(1));
    }

    @Test
    public void createWithRawTemplateMixedEscapesAndDelimiters() {
        // Mix of escaped nulls (\\0) and actual NUL delimiters (\0).
        // The \0 acts as a value placeholder, so we need to provide a value for it.
        TemplateString result = TemplateBuilder.create("a\\0b\0c\\0d", "placeholder");
        assertEquals(2, result.fragments().size());
        assertEquals("a\0b", result.fragments().get(0));
        assertEquals("c\0d", result.fragments().get(1));
        assertEquals(1, result.values().size());
    }
}
