package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import st.orm.SqlLog;

/**
 * Tests for {@link SqlLogInterceptor}.
 */
public class SqlLogInterceptorTest {

    // Test interface without @SqlLog.
    interface PlainRepository {
        void find();
    }

    // Test interface with type-level @SqlLog.
    @SqlLog
    interface LoggedRepository {
        void find();
        @SqlLog(name = "custom.logger")
        void findCustom();
    }

    @Test
    public void testResolveNoAnnotation() throws NoSuchMethodException {
        Method method = PlainRepository.class.getMethod("find");
        SqlLog result = SqlLogInterceptor.resolve(PlainRepository.class, method);
        assertNull(result);
    }

    @Test
    public void testResolveTypeLevelAnnotation() throws NoSuchMethodException {
        Method method = LoggedRepository.class.getMethod("find");
        SqlLog result = SqlLogInterceptor.resolve(LoggedRepository.class, method);
        assertNotNull(result);
        assertEquals("", result.name());
    }

    @Test
    public void testResolveMethodLevelOverridesTypeLevel() throws NoSuchMethodException {
        Method method = LoggedRepository.class.getMethod("findCustom");
        SqlLog result = SqlLogInterceptor.resolve(LoggedRepository.class, method);
        assertNotNull(result);
        assertEquals("custom.logger", result.name());
    }

    @Test
    public void testWrapIfNeededNullSqlLog() throws Throwable {
        String result = SqlLogInterceptor.wrapIfNeeded(null, PlainRepository.class, "find()", () -> "hello");
        assertEquals("hello", result);
    }

    @Test
    public void testWrapIfNeededNullSqlLogPassesThroughException() {
        assertThrows(RuntimeException.class, () ->
                SqlLogInterceptor.wrapIfNeeded(null, PlainRepository.class, "find()", () -> {
                    throw new RuntimeException("test error");
                })
        );
    }

    @Test
    public void testWrapIfNeededWithAnnotation() throws Throwable {
        // Get the type-level @SqlLog annotation.
        SqlLog sqlLog = LoggedRepository.class.getAnnotation(SqlLog.class);
        assertNotNull(sqlLog);
        // When logger is not enabled for the given level, it should just call through.
        String result = SqlLogInterceptor.wrapIfNeeded(sqlLog, LoggedRepository.class, "find()", () -> "result");
        assertEquals("result", result);
    }

    @Test
    public void testWrapIfNeededWithCustomAnnotation() throws Throwable {
        // Get the method-level @SqlLog annotation.
        Method method = LoggedRepository.class.getMethod("findCustom");
        SqlLog sqlLog = method.getAnnotation(SqlLog.class);
        assertNotNull(sqlLog);
        assertEquals("custom.logger", sqlLog.name());
        String result = SqlLogInterceptor.wrapIfNeeded(sqlLog, LoggedRepository.class, "findCustom()", () -> "value");
        assertEquals("value", result);
    }

    @Test
    public void testWrapIfNeededWithAnnotationException() {
        SqlLog sqlLog = LoggedRepository.class.getAnnotation(SqlLog.class);
        assertNotNull(sqlLog);
        assertThrows(RuntimeException.class, () ->
                SqlLogInterceptor.wrapIfNeeded(sqlLog, LoggedRepository.class, "find()", () -> {
                    throw new RuntimeException("sql error");
                })
        );
    }
}
