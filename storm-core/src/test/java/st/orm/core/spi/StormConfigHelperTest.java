package st.orm.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import st.orm.StormConfig;

/**
 * Tests for {@link StormConfigHelper}.
 */
public class StormConfigHelperTest {

    @Test
    public void getIntReturnsConfiguredValue() {
        var config = StormConfig.of(Map.of("key", "42"));
        assertEquals(42, StormConfigHelper.getInt(config, "key", 10));
    }

    @Test
    public void getIntReturnsDefaultWhenMissing() {
        var config = StormConfig.of(Map.of());
        assertEquals(10, StormConfigHelper.getInt(config, "key", 10));
    }

    @Test
    public void getIntReturnsDefaultOnInvalidValue() {
        var config = StormConfig.of(Map.of("key", "not-a-number"));
        assertEquals(10, StormConfigHelper.getInt(config, "key", 10));
    }

    @Test
    public void getIntTrimsWhitespace() {
        var config = StormConfig.of(Map.of("key", "  7  "));
        assertEquals(7, StormConfigHelper.getInt(config, "key", 10));
    }

    @Test
    public void getBooleanReturnsTrueWhenConfigured() {
        var config = StormConfig.of(Map.of("key", "true"));
        assertTrue(StormConfigHelper.getBoolean(config, "key", false));
    }

    @Test
    public void getBooleanReturnsFalseForNonBooleanValue() {
        var config = StormConfig.of(Map.of("key", "yes"));
        assertFalse(StormConfigHelper.getBoolean(config, "key", true));
    }

    @Test
    public void getBooleanReturnsDefaultWhenMissing() {
        var config = StormConfig.of(Map.of());
        assertTrue(StormConfigHelper.getBoolean(config, "key", true));
    }

    @Test
    public void getBooleanTrimsWhitespace() {
        var config = StormConfig.of(Map.of("key", " true "));
        assertTrue(StormConfigHelper.getBoolean(config, "key", false));
    }

    enum Color { RED, GREEN, BLUE }

    @Test
    public void getEnumReturnsConfiguredValue() {
        var config = StormConfig.of(Map.of("key", "GREEN"));
        assertEquals(Color.GREEN, StormConfigHelper.getEnum(config, "key", Color.class, Color.RED));
    }

    @Test
    public void getEnumIsCaseInsensitive() {
        var config = StormConfig.of(Map.of("key", "blue"));
        assertEquals(Color.BLUE, StormConfigHelper.getEnum(config, "key", Color.class, Color.RED));
    }

    @Test
    public void getEnumReturnsDefaultWhenMissing() {
        var config = StormConfig.of(Map.of());
        assertEquals(Color.RED, StormConfigHelper.getEnum(config, "key", Color.class, Color.RED));
    }

    @Test
    public void getEnumReturnsDefaultOnInvalidValue() {
        var config = StormConfig.of(Map.of("key", "YELLOW"));
        assertEquals(Color.RED, StormConfigHelper.getEnum(config, "key", Color.class, Color.RED));
    }

    @Test
    public void getEnumTrimsWhitespace() {
        var config = StormConfig.of(Map.of("key", "  green  "));
        assertEquals(Color.GREEN, StormConfigHelper.getEnum(config, "key", Color.class, Color.RED));
    }

    @Test
    public void getDurationReadsAUnitSuffixABareNumberOrIso() {
        assertEquals(Duration.ofMillis(200), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "200ms")), "key", null));
        assertEquals(Duration.ofMillis(200), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "200")), "key", null));
        assertEquals(Duration.ofSeconds(2), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "2s")), "key", null));
        assertEquals(Duration.ofMillis(1500), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "1.5s")), "key", null));
        assertEquals(Duration.ofMinutes(1), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "1m")), "key", null));
        assertEquals(Duration.ofMillis(200), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "PT0.2S")), "key", null));
        assertEquals(Duration.ofMillis(200), StormConfigHelper.getDuration(StormConfig.of(Map.of("key", " 200 ms ")), "key", null));
    }

    @Test
    public void getDurationReturnsDefaultWhenMissingOrInvalid() {
        assertNull(StormConfigHelper.getDuration(StormConfig.of(Map.of()), "key", null));
        assertEquals(Duration.ofSeconds(1),
                StormConfigHelper.getDuration(StormConfig.of(Map.of("key", "soon")), "key", Duration.ofSeconds(1)));
    }
}
