package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StormConfigTest {

    @Test
    void defaultsReturnsNonNull() {
        StormConfig config = StormConfig.defaults();
        assertNotNull(config);
    }

    @Test
    void ofCreatesConfigWithProperties() {
        StormConfig config = StormConfig.of(Map.of("key", "value"));
        assertEquals("value", config.getProperty("key"));
    }

    @Test
    void getPropertyReturnsNullForMissingKey() {
        StormConfig config = StormConfig.of(Map.of());
        assertNull(config.getProperty("nonexistent.key.that.should.not.exist"));
    }

    @Test
    void getPropertyWithDefaultReturnsDefaultForMissingKey() {
        StormConfig config = StormConfig.of(Map.of());
        assertEquals("default", config.getProperty("nonexistent.key.that.should.not.exist", "default"));
    }

    @Test
    void getPropertyWithDefaultReturnsValueWhenPresent() {
        StormConfig config = StormConfig.of(Map.of("key", "value"));
        assertEquals("value", config.getProperty("key", "default"));
    }

    @Test
    void getPropertyFallsBackToSystemProperty() {
        String javaVersion = StormConfig.defaults().getProperty("java.version");
        assertNotNull(javaVersion);
    }
}
