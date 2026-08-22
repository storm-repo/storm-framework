package st.orm.core.template.impl;

/**
 * Lets tests outside this package reset the slow log's per-shape state, which is static and shared across the
 * tests of one JVM.
 */
public final class SlowStatementLogTestSupport {

    private SlowStatementLogTestSupport() {
    }

    public static void reset() {
        SlowStatementLog.reset();
    }

    /** Returns the number of shapes tracked before new ones share the untracked budget. */
    public static int maxShapes() {
        return SlowStatementLog.maxShapes;
    }

    /**
     * Sets the number of shapes tracked before new ones share the untracked budget, so a test can exhaust the map
     * without generating four thousand shapes.
     */
    public static void maxShapes(int maxShapes) {
        SlowStatementLog.maxShapes = maxShapes;
    }
}
