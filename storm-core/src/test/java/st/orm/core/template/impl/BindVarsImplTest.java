package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.PersistenceException;

/**
 * Tests for {@link BindVarsImpl}.
 */
public class BindVarsImplTest {

    @Test
    public void testGetHandle() {
        BindVarsImpl bindVars = new BindVarsImpl();
        BindVarsHandle handle = bindVars.getHandle();
        assertNotNull(handle);
    }

    @Test
    public void testHandleThrowsWhenNoBatchListener() {
        BindVarsImpl bindVars = new BindVarsImpl();
        BindVarsHandle handle = bindVars.getHandle();
        // Using handle without setting batch listener should throw.
        assertThrows(IllegalStateException.class, () -> handle.addBatch(null));
    }

    @Test
    public void testSetBatchListenerTwiceThrows() {
        BindVarsImpl bindVars = new BindVarsImpl();
        bindVars.setBatchListener(params -> {});
        assertThrows(PersistenceException.class, () -> bindVars.setBatchListener(params -> {}));
    }

    @Test
    public void testSetRecordListenerTwiceThrows() {
        BindVarsImpl bindVars = new BindVarsImpl();
        bindVars.setRecordListener(record -> {});
        assertThrows(PersistenceException.class, () -> bindVars.setRecordListener(record -> {}));
    }

    @Test
    public void testToString() {
        BindVarsImpl bindVars = new BindVarsImpl();
        String result = bindVars.toString();
        assertNotNull(result);
        assertTrue(result.startsWith("BindVarsImpl@"));
    }

    @Test
    public void testHandleThrowsWhenNoParameterExtractors() {
        BindVarsImpl bindVars = new BindVarsImpl();
        bindVars.setBatchListener(params -> {});
        BindVarsHandle handle = bindVars.getHandle();
        // No parameter extractors set.
        assertThrows(IllegalStateException.class, () -> handle.addBatch(null));
    }
}
