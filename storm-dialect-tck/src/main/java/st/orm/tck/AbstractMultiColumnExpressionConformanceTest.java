package st.orm.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;
import st.orm.Operator;
import st.orm.SqlTemplateException;
import st.orm.core.template.SqlDialect;

/**
 * The predicate a dialect writes for a key that spans several columns.
 *
 * <p>Two capabilities account for every difference between dialects, so this suite states them rather than holding a
 * table of expected strings: {@link #supportsRowValueIn()} for {@code (a, b) IN ((?, ?), (?, ?))} and
 * {@link #supportsRowValueComparison()} for {@code (a, b) > (?, ?)}. A dialect without either gets the expansion into
 * plain comparisons, which is what makes the predicate correct rather than merely shorter.
 *
 * <p>This suite needs no database: it calls the dialect directly.
 */
public abstract class AbstractMultiColumnExpressionConformanceTest {

    /** The dialect under test. */
    protected abstract SqlDialect dialect();

    /** Whether {@code IN} accepts a row value, rather than expanding to {@code OR} over per-column equality. */
    protected boolean supportsRowValueIn() {
        return true;
    }

    /** Whether an ordering comparison accepts a row value, rather than expanding to a lexicographic chain. */
    protected boolean supportsRowValueComparison() {
        return true;
    }

    protected static SequencedMap<String, Object> row(String column1, Object value1,
                                                      String column2, Object value2) {
        SequencedMap<String, Object> map = new LinkedHashMap<>();
        map.put(column1, value1);
        map.put(column2, value2);
        return map;
    }

    protected static SequencedMap<String, Object> row(String column1, Object value1,
                                                      String column2, Object value2,
                                                      String column3, Object value3) {
        SequencedMap<String, Object> map = new LinkedHashMap<>();
        map.put(column1, value1);
        map.put(column2, value2);
        map.put(column3, value3);
        return map;
    }

    private String expression(Operator operator, List<SequencedMap<String, Object>> values)
            throws SqlTemplateException {
        return dialect().multiColumnExpression(operator, values, value -> "?");
    }

    private String comparison(String rowValue, String expanded) {
        return supportsRowValueComparison() ? rowValue : expanded;
    }

    @Test
    void equals_twoColumns() throws SqlTemplateException {
        assertEquals("a = ? AND b = ?", expression(Operator.EQUALS, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void equals_threeColumns() throws SqlTemplateException {
        assertEquals("a = ? AND b = ? AND c = ?",
                expression(Operator.EQUALS, List.of(row("a", 1, "b", 2, "c", 3))));
    }

    @Test
    void notEquals_twoColumns() throws SqlTemplateException {
        assertEquals("NOT (a = ? AND b = ?)", expression(Operator.NOT_EQUALS, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void in_multipleRows() throws SqlTemplateException {
        var values = List.of(row("a", 1, "b", 2), row("a", 3, "b", 4));
        assertEquals(supportsRowValueIn()
                        ? "(a, b) IN ((?, ?), (?, ?))"
                        : "(a = ? AND b = ?) OR (a = ? AND b = ?)",
                expression(Operator.IN, values));
    }

    @Test
    void notIn_multipleRows() throws SqlTemplateException {
        var values = List.of(row("a", 1, "b", 2), row("a", 3, "b", 4));
        assertEquals(supportsRowValueIn()
                        ? "(a, b) NOT IN ((?, ?), (?, ?))"
                        : "NOT ((a = ? AND b = ?) OR (a = ? AND b = ?))",
                expression(Operator.NOT_IN, values));
    }

    @Test
    void greaterThan_twoColumns() throws SqlTemplateException {
        assertEquals(comparison("(a, b) > (?, ?)", "(a > ? OR (a = ? AND b > ?))"),
                expression(Operator.GREATER_THAN, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void greaterThan_threeColumns() throws SqlTemplateException {
        assertEquals(comparison("(a, b, c) > (?, ?, ?)",
                        "(a > ? OR (a = ? AND b > ?) OR (a = ? AND b = ? AND c > ?))"),
                expression(Operator.GREATER_THAN, List.of(row("a", 1, "b", 2, "c", 3))));
    }

    @Test
    void greaterThanOrEqual_twoColumns() throws SqlTemplateException {
        assertEquals(comparison("(a, b) >= (?, ?)", "(a > ? OR (a = ? AND b >= ?))"),
                expression(Operator.GREATER_THAN_OR_EQUAL, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void lessThan_twoColumns() throws SqlTemplateException {
        assertEquals(comparison("(a, b) < (?, ?)", "(a < ? OR (a = ? AND b < ?))"),
                expression(Operator.LESS_THAN, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void lessThanOrEqual_twoColumns() throws SqlTemplateException {
        assertEquals(comparison("(a, b) <= (?, ?)", "(a < ? OR (a = ? AND b <= ?))"),
                expression(Operator.LESS_THAN_OR_EQUAL, List.of(row("a", 1, "b", 2))));
    }

    @Test
    void between_twoColumns() throws SqlTemplateException {
        var values = List.of(row("a", 1, "b", 2), row("a", 5, "b", 6));
        assertEquals(comparison("(a, b) BETWEEN (?, ?) AND (?, ?)",
                        "((a > ? OR (a = ? AND b >= ?)) AND (a < ? OR (a = ? AND b <= ?)))"),
                expression(Operator.BETWEEN, values));
    }

    @Test
    void isNull_twoColumns() throws SqlTemplateException {
        assertEquals("a IS NULL AND b IS NULL",
                expression(Operator.IS_NULL, List.of(row("a", null, "b", null))));
    }

    @Test
    void isNotNull_twoColumns() throws SqlTemplateException {
        assertEquals("a IS NOT NULL AND b IS NOT NULL",
                expression(Operator.IS_NOT_NULL, List.of(row("a", null, "b", null))));
    }

    @Test
    void parameterBindingOrder_greaterThan() throws SqlTemplateException {
        var bindOrder = new StringBuilder();
        dialect().multiColumnExpression(Operator.GREATER_THAN, List.of(row("a", "v1", "b", "v2")), value -> {
            bindOrder.append(value).append(',');
            return "?";
        });
        // The expansion repeats the leading column, so it binds it twice.
        assertEquals(supportsRowValueComparison() ? "v1,v2," : "v1,v1,v2,", bindOrder.toString());
    }

    @Test
    void parameterBindingOrder_greaterThan_threeColumns() throws SqlTemplateException {
        var bindOrder = new StringBuilder();
        dialect().multiColumnExpression(Operator.GREATER_THAN, List.of(row("a", "v1", "b", "v2", "c", "v3")),
                value -> {
                    bindOrder.append(value).append(',');
                    return "?";
                });
        assertEquals(supportsRowValueComparison() ? "v1,v2,v3," : "v1,v1,v2,v1,v2,v3,", bindOrder.toString());
    }

    @Test
    void parameterBindingOrder_in_multipleRows() throws SqlTemplateException {
        var bindOrder = new StringBuilder();
        dialect().multiColumnExpression(Operator.IN,
                List.of(row("a", "v1", "b", "v2"), row("a", "v3", "b", "v4")), value -> {
                    bindOrder.append(value).append(',');
                    return "?";
                });
        assertEquals("v1,v2,v3,v4,", bindOrder.toString());
    }
}
