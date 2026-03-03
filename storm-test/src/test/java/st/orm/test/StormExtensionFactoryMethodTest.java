package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Tests for the factory method resolution logic in {@link StormExtension}, covering the
 * {@code hasFactoryMethod} and {@code invokeFactoryMethod} paths.
 */
class StormExtensionFactoryMethodTest {

    /**
     * A class with a static {@code of(DataSource)} factory method for testing factory resolution.
     */
    public static class WithStaticFactory {
        private final DataSource dataSource;

        private WithStaticFactory(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public static WithStaticFactory of(DataSource dataSource) {
            return new WithStaticFactory(dataSource);
        }

        public DataSource dataSource() {
            return dataSource;
        }
    }

    /**
     * A class without any factory method, used to test the negative case.
     */
    public static class WithoutFactory {
    }

    /**
     * A class with a non-static {@code of(DataSource)} method, which should not be detected.
     */
    public static class WithNonStaticFactory {
        @SuppressWarnings("unused")
        public WithNonStaticFactory of(DataSource dataSource) {
            return this;
        }
    }

    @Test
    void hasFactoryMethodShouldReturnTrueForClassWithStaticOfMethod() throws Exception {
        Method hasFactoryMethod = StormExtension.class.getDeclaredMethod("hasFactoryMethod", Class.class);
        hasFactoryMethod.setAccessible(true);
        assertTrue((Boolean) hasFactoryMethod.invoke(null, WithStaticFactory.class));
    }

    @Test
    void hasFactoryMethodShouldReturnFalseForClassWithoutFactoryMethod() throws Exception {
        Method hasFactoryMethod = StormExtension.class.getDeclaredMethod("hasFactoryMethod", Class.class);
        hasFactoryMethod.setAccessible(true);
        assertFalse((Boolean) hasFactoryMethod.invoke(null, WithoutFactory.class));
    }

    @Test
    void hasFactoryMethodShouldReturnFalseForClassWithNonStaticOfMethod() throws Exception {
        Method hasFactoryMethod = StormExtension.class.getDeclaredMethod("hasFactoryMethod", Class.class);
        hasFactoryMethod.setAccessible(true);
        assertFalse((Boolean) hasFactoryMethod.invoke(null, WithNonStaticFactory.class));
    }

    @Test
    void hasFactoryMethodShouldReturnFalseForDataSource() throws Exception {
        // DataSource is a known supported type via supportsParameter, but hasFactoryMethod
        // looks for static of(DataSource) methods, which DataSource itself does not have.
        Method hasFactoryMethod = StormExtension.class.getDeclaredMethod("hasFactoryMethod", Class.class);
        hasFactoryMethod.setAccessible(true);
        // DataSource does not have a static of(DataSource) method.
        assertFalse((Boolean) hasFactoryMethod.invoke(null, DataSource.class));
    }

    @Test
    void invokeFactoryMethodShouldCreateInstanceViaStaticOf() throws Exception {
        // Test the invokeFactoryMethod logic directly using reflection.
        Method invokeFactoryMethod = StormExtension.class.getDeclaredMethod(
                "invokeFactoryMethod", Class.class, DataSource.class);
        invokeFactoryMethod.setAccessible(true);

        // Use a minimal DataSource for the test. We just need a non-null argument.
        DataSource dummyDataSource = new javax.sql.DataSource() {
            @Override public java.sql.Connection getConnection() { return null; }
            @Override public java.sql.Connection getConnection(String u, String p) { return null; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };

        Object result = invokeFactoryMethod.invoke(null, WithStaticFactory.class, dummyDataSource);
        assertNotNull(result);
        assertTrue(result instanceof WithStaticFactory);
    }
}
