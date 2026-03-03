package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultJoinTypeTest {

    @Test
    void innerJoinSql() {
        assertEquals("INNER JOIN", DefaultJoinType.INNER.sql());
    }

    @Test
    void innerJoinHasOnClause() {
        assertTrue(DefaultJoinType.INNER.hasOnClause());
    }

    @Test
    void innerJoinIsNotOuter() {
        assertFalse(DefaultJoinType.INNER.isOuter());
    }

    @Test
    void crossJoinSql() {
        assertEquals("CROSS JOIN", DefaultJoinType.CROSS.sql());
    }

    @Test
    void crossJoinHasNoOnClause() {
        assertFalse(DefaultJoinType.CROSS.hasOnClause());
    }

    @Test
    void crossJoinIsNotOuter() {
        assertFalse(DefaultJoinType.CROSS.isOuter());
    }

    @Test
    void leftJoinSql() {
        assertEquals("LEFT JOIN", DefaultJoinType.LEFT.sql());
    }

    @Test
    void leftJoinHasOnClause() {
        assertTrue(DefaultJoinType.LEFT.hasOnClause());
    }

    @Test
    void leftJoinIsOuter() {
        assertTrue(DefaultJoinType.LEFT.isOuter());
    }

    @Test
    void rightJoinSql() {
        assertEquals("RIGHT JOIN", DefaultJoinType.RIGHT.sql());
    }

    @Test
    void rightJoinHasOnClause() {
        assertTrue(DefaultJoinType.RIGHT.hasOnClause());
    }

    @Test
    void rightJoinIsOuter() {
        assertTrue(DefaultJoinType.RIGHT.isOuter());
    }
}
