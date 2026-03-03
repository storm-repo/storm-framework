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
    void equalsOperatorWithNullColumn() {
        // When column is null, the operator format still produces SQL with "null" as string
        String result = Operator.EQUALS.format(null, "?");
        assertEquals("null = ?", result);
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
    void notEqualsWithNullColumnFormatsWithNull() {
        // When column is null, operators produce SQL with "null" string (no exception).
        String result = Operator.NOT_EQUALS.format(null, "?");
        assertEquals("null <> ?", result);
    }

    @Test
    void likeWithNullColumnFormatsWithNull() {
        String result = Operator.LIKE.format(null, "?");
        assertEquals("null LIKE ?", result);
    }

    @Test
    void notLikeWithNullColumnFormatsWithNull() {
        String result = Operator.NOT_LIKE.format(null, "?");
        assertEquals("null NOT LIKE ?", result);
    }

    @Test
    void greaterThanWithNullColumnFormatsWithNull() {
        String result = Operator.GREATER_THAN.format(null, "?");
        assertEquals("null > ?", result);
    }

    @Test
    void greaterThanOrEqualWithNullColumnFormatsWithNull() {
        String result = Operator.GREATER_THAN_OR_EQUAL.format(null, "?");
        assertEquals("null >= ?", result);
    }

    @Test
    void lessThanWithNullColumnFormatsWithNull() {
        String result = Operator.LESS_THAN.format(null, "?");
        assertEquals("null < ?", result);
    }

    @Test
    void lessThanOrEqualWithNullColumnFormatsWithNull() {
        String result = Operator.LESS_THAN_OR_EQUAL.format(null, "?");
        assertEquals("null <= ?", result);
    }

    @Test
    void betweenWithNullColumnFormatsWithNull() {
        String result = Operator.BETWEEN.format(null, "?", "?");
        assertEquals("null BETWEEN ? AND ?", result);
    }

    @Test
    void isTrueWithNullColumnFormatsWithNull() {
        String result = Operator.IS_TRUE.format(null);
        assertEquals("null IS TRUE", result);
    }

    @Test
    void isFalseWithNullColumnFormatsWithNull() {
        String result = Operator.IS_FALSE.format(null);
        assertEquals("null IS FALSE", result);
    }

    @Test
    void isNullWithNullColumnFormatsWithNull() {
        String result = Operator.IS_NULL.format(null);
        assertEquals("null IS NULL", result);
    }

    @Test
    void isNotNullWithNullColumnFormatsWithNull() {
        String result = Operator.IS_NOT_NULL.format(null);
        assertEquals("null IS NOT NULL", result);
    }

    @Test
    void inWithNullColumnFormatsWithNull() {
        String result = Operator.IN.format(null, "?");
        assertEquals("null IN (?)", result);
    }

    @Test
    void notInWithNullColumnFormatsWithNull() {
        String result = Operator.NOT_IN.format(null, "?");
        assertEquals("null NOT IN (?)", result);
    }
}
