package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperatorTest {

    @Test
    void inOperatorWithZeroPlaceholders() {
        String result = Operator.IN.format("col");
        assertEquals("1 <> 1", result);
    }

    @Test
    void inOperatorWithSinglePlaceholder() {
        String result = Operator.IN.format("col", "?");
        assertEquals("col IN (?)", result);
    }

    @Test
    void inOperatorWithMultiplePlaceholders() {
        String result = Operator.IN.format("col", "?", "?", "?");
        assertEquals("col IN (?, ?, ?)", result);
    }

    @Test
    void notInOperatorWithZeroPlaceholders() {
        String result = Operator.NOT_IN.format("col");
        assertEquals("1 = 1", result);
    }

    @Test
    void notInOperatorWithSinglePlaceholder() {
        String result = Operator.NOT_IN.format("col", "?");
        assertEquals("col NOT IN (?)", result);
    }

    @Test
    void notInOperatorWithMultiplePlaceholders() {
        String result = Operator.NOT_IN.format("col", "?", "?");
        assertEquals("col NOT IN (?, ?)", result);
    }

    @Test
    void equalsOperator() {
        String result = Operator.EQUALS.format("col", "?");
        assertEquals("col = ?", result);
    }

    @Test
    void equalsOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.EQUALS.format("col", "?", "?"));
    }

    @Test
    void equalsOperatorRequiresAtLeastOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.EQUALS.format("col"));
    }

    @Test
    void notEqualsOperator() {
        String result = Operator.NOT_EQUALS.format("col", "?");
        assertEquals("col <> ?", result);
    }

    @Test
    void notEqualsOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.NOT_EQUALS.format("col", "?", "?"));
    }

    @Test
    void likeOperator() {
        String result = Operator.LIKE.format("col", "?");
        assertEquals("col LIKE ?", result);
    }

    @Test
    void likeOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LIKE.format("col", "?", "?"));
    }

    @Test
    void notLikeOperator() {
        String result = Operator.NOT_LIKE.format("col", "?");
        assertEquals("col NOT LIKE ?", result);
    }

    @Test
    void notLikeOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.NOT_LIKE.format("col", "?", "?"));
    }

    @Test
    void greaterThanOperator() {
        String result = Operator.GREATER_THAN.format("col", "?");
        assertEquals("col > ?", result);
    }

    @Test
    void greaterThanOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.GREATER_THAN.format("col"));
    }

    @Test
    void greaterThanOrEqualOperator() {
        String result = Operator.GREATER_THAN_OR_EQUAL.format("col", "?");
        assertEquals("col >= ?", result);
    }

    @Test
    void greaterThanOrEqualOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.GREATER_THAN_OR_EQUAL.format("col"));
    }

    @Test
    void lessThanOperator() {
        String result = Operator.LESS_THAN.format("col", "?");
        assertEquals("col < ?", result);
    }

    @Test
    void lessThanOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LESS_THAN.format("col"));
    }

    @Test
    void lessThanOrEqualOperator() {
        String result = Operator.LESS_THAN_OR_EQUAL.format("col", "?");
        assertEquals("col <= ?", result);
    }

    @Test
    void lessThanOrEqualOperatorRequiresOnePlaceholder() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LESS_THAN_OR_EQUAL.format("col"));
    }

    @Test
    void betweenOperator() {
        String result = Operator.BETWEEN.format("col", "?", "?");
        assertEquals("col BETWEEN ? AND ?", result);
    }

    @Test
    void betweenOperatorRequiresTwoPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> Operator.BETWEEN.format("col", "?"));
    }

    @Test
    void isTrueOperator() {
        String result = Operator.IS_TRUE.format("col");
        assertEquals("col IS TRUE", result);
    }

    @Test
    void isTrueOperatorRequiresZeroPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_TRUE.format("col", "?"));
    }

    @Test
    void isFalseOperator() {
        String result = Operator.IS_FALSE.format("col");
        assertEquals("col IS FALSE", result);
    }

    @Test
    void isFalseOperatorRequiresZeroPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_FALSE.format("col", "?"));
    }

    @Test
    void isNullOperator() {
        String result = Operator.IS_NULL.format("col");
        assertEquals("col IS NULL", result);
    }

    @Test
    void isNullOperatorRequiresZeroPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_NULL.format("col", "?"));
    }

    @Test
    void isNotNullOperator() {
        String result = Operator.IS_NOT_NULL.format("col");
        assertEquals("col IS NOT NULL", result);
    }

    @Test
    void isNotNullOperatorRequiresZeroPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_NOT_NULL.format("col", "?"));
    }

    @Test
    void equalsOperatorWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.EQUALS.format(null, "?"));
    }

    @Test
    void betweenWithThreePlaceholdersThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.BETWEEN.format("col", "?", "?", "?"));
    }

    @Test
    void betweenWithZeroPlaceholdersThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.BETWEEN.format("col"));
    }

    @Test
    void notEqualsWithZeroPlaceholdersThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.NOT_EQUALS.format("col"));
    }

    @Test
    void notLikeWithZeroPlaceholdersThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.NOT_LIKE.format("col"));
    }

    @Test
    void isTrueWithOnePlaceholderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.IS_TRUE.format("col", "?"));
    }

    @Test
    void isFalseWithOnePlaceholderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.IS_FALSE.format("col", "?"));
    }

    @Test
    void isNullWithOnePlaceholderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.IS_NULL.format("col", "?"));
    }

    @Test
    void isNotNullWithOnePlaceholderThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Operator.IS_NOT_NULL.format("col", "?"));
    }

    @Test
    void notEqualsWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.NOT_EQUALS.format(null, "?"));
    }

    @Test
    void likeWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LIKE.format(null, "?"));
    }

    @Test
    void notLikeWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.NOT_LIKE.format(null, "?"));
    }

    @Test
    void greaterThanWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.GREATER_THAN.format(null, "?"));
    }

    @Test
    void greaterThanOrEqualWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.GREATER_THAN_OR_EQUAL.format(null, "?"));
    }

    @Test
    void lessThanWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LESS_THAN.format(null, "?"));
    }

    @Test
    void lessThanOrEqualWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.LESS_THAN_OR_EQUAL.format(null, "?"));
    }

    @Test
    void betweenWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.BETWEEN.format(null, "?", "?"));
    }

    @Test
    void isTrueWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_TRUE.format(null));
    }

    @Test
    void isFalseWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_FALSE.format(null));
    }

    @Test
    void isNullWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_NULL.format(null));
    }

    @Test
    void isNotNullWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IS_NOT_NULL.format(null));
    }

    @Test
    void inWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.IN.format(null, "?"));
    }

    @Test
    void inWithNullColumnAndZeroPlaceholdersFormatsWithoutColumn() {
        // The zero-placeholder form renders a constant expression and does not reference the column.
        String result = Operator.IN.format(null);
        assertEquals("1 <> 1", result);
    }

    @Test
    void notInWithNullColumnThrows() {
        assertThrows(IllegalArgumentException.class, () -> Operator.NOT_IN.format(null, "?"));
    }
}
