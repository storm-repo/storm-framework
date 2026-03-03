package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JoinTypeTest {

    @Test
    void innerStaticMethod() {
        JoinType joinType = JoinType.inner();
        assertEquals("INNER JOIN", joinType.sql());
        assertTrue(joinType.hasOnClause());
        assertFalse(joinType.isOuter());
    }

    @Test
    void crossStaticMethod() {
        JoinType joinType = JoinType.cross();
        assertEquals("CROSS JOIN", joinType.sql());
    }

    @Test
    void leftStaticMethod() {
        JoinType joinType = JoinType.left();
        assertEquals("LEFT JOIN", joinType.sql());
        assertTrue(joinType.isOuter());
    }

    @Test
    void rightStaticMethod() {
        JoinType joinType = JoinType.right();
        assertEquals("RIGHT JOIN", joinType.sql());
        assertTrue(joinType.isOuter());
    }

    @Test
    void defaultHasOnClauseIsTrue() {
        JoinType custom = () -> "CUSTOM JOIN";
        assertTrue(custom.hasOnClause());
    }

    @Test
    void defaultIsOuterIsFalse() {
        JoinType custom = () -> "CUSTOM JOIN";
        assertFalse(custom.isOuter());
    }
}
