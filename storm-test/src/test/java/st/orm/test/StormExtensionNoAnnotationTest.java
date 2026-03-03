package st.orm.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests the path in {@link StormExtension#beforeAll} where no {@link StormTest} annotation is present.
 * The extension should simply return without creating a DataSource, and the test class should
 * still be able to execute tests normally.
 */
@ExtendWith(StormExtension.class)
class StormExtensionNoAnnotationTest {

    @Test
    void extensionWithoutStormTestAnnotationShouldNotThrow() {
        // When @StormTest annotation is absent (we only use @ExtendWith directly),
        // beforeAll should return early and no DataSource is stored.
        // The test passing at all confirms the extension did not throw during beforeAll.
    }
}
