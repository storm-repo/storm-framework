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
}
