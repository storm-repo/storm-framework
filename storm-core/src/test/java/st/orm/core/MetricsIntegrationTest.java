package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import st.orm.core.repository.impl.DirtyCheckMetrics;
import st.orm.core.spi.EntityCacheMetrics;
import st.orm.core.template.impl.TemplateMetrics;

/**
 * Tests for JMX metrics singleton classes: DirtyCheckMetrics, EntityCacheMetrics, TemplateMetrics.
 */
@SuppressWarnings("ALL")
public class MetricsIntegrationTest {

    // -----------------------------------------------------------------------
    // DirtyCheckMetrics
    // -----------------------------------------------------------------------

    @Test
    public void testDirtyCheckMetricsGetInstance() {
        var metrics = DirtyCheckMetrics.getInstance();
        assertNotNull(metrics);
        // Singleton - same instance.
        assertTrue(metrics == DirtyCheckMetrics.getInstance());
    }

    @Test
    public void testDirtyCheckMetricsResetAndRecordClean() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();
        assertEquals(0, metrics.getChecks());
        assertEquals(0, metrics.getClean());
        assertEquals(0, metrics.getDirty());

        metrics.recordClean();
        assertEquals(1, metrics.getChecks());
        assertEquals(1, metrics.getClean());
        assertEquals(0, metrics.getDirty());
    }

    @Test
    public void testDirtyCheckMetricsRecordCleanIdentityMatch() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordCleanIdentityMatch();
        assertEquals(1, metrics.getChecks());
        assertEquals(1, metrics.getClean());
        assertEquals(1, metrics.getIdentityMatches());
    }

    @Test
    public void testDirtyCheckMetricsRecordDirty() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordDirty();
        assertEquals(1, metrics.getChecks());
        assertEquals(1, metrics.getDirty());
        assertEquals(0, metrics.getCacheMisses());
    }

    @Test
    public void testDirtyCheckMetricsRecordDirtyCacheMiss() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordDirtyCacheMiss();
        assertEquals(1, metrics.getChecks());
        assertEquals(1, metrics.getDirty());
        assertEquals(1, metrics.getCacheMisses());
    }

    @Test
    public void testDirtyCheckMetricsCleanRatioPercent() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();
        // Zero division case.
        assertEquals(0, metrics.getCleanRatioPercent());

        metrics.recordClean();
        metrics.recordClean();
        metrics.recordDirty();
        // 2 clean / 3 total = 66%.
        assertEquals(66, metrics.getCleanRatioPercent());
    }

    @Test
    public void testDirtyCheckMetricsModeChecks() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordEntityModeCheck();
        metrics.recordFieldModeCheck();
        assertEquals(1, metrics.getEntityModeChecks());
        assertEquals(1, metrics.getFieldModeChecks());
    }

    @Test
    public void testDirtyCheckMetricsStrategyChecks() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordInstanceStrategyCheck();
        metrics.recordValueStrategyCheck();
        assertEquals(1, metrics.getInstanceStrategyChecks());
        assertEquals(1, metrics.getValueStrategyChecks());
    }

    @Test
    public void testDirtyCheckMetricsFieldCounters() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordFieldClean();
        metrics.recordFieldClean();
        metrics.recordFieldDirty();
        assertEquals(3, metrics.getFieldComparisons());
        assertEquals(2, metrics.getFieldClean());
        assertEquals(1, metrics.getFieldDirty());
    }

    @Test
    public void testDirtyCheckMetricsShapes() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.recordNewShape("City");
        metrics.recordNewShape("City");
        metrics.recordNewShape("Owner");
        assertEquals(3, metrics.getShapes());
        assertEquals(2, metrics.getEntityTypes());

        Map<String, Long> shapesPerEntity = metrics.getShapesPerEntity();
        assertEquals(2, shapesPerEntity.get("City"));
        assertEquals(1, shapesPerEntity.get("Owner"));
    }

    @Test
    public void testDirtyCheckMetricsRegisterEntity() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.reset();

        metrics.registerEntity("City", "FIELD", "VALUE", 8);
        Map<String, String> updateModes = metrics.getUpdateModePerEntity();
        Map<String, String> dirtyChecks = metrics.getDirtyCheckPerEntity();
        Map<String, Integer> maxShapes = metrics.getMaxShapesPerEntity();

        assertEquals("FIELD", updateModes.get("City"));
        assertEquals("VALUE", dirtyChecks.get("City"));
        assertEquals(8, maxShapes.get("City"));
    }

    @Test
    public void testDirtyCheckMetricsResetClearsAll() {
        var metrics = DirtyCheckMetrics.getInstance();
        metrics.recordClean();
        metrics.recordDirty();
        metrics.recordFieldClean();
        metrics.recordFieldDirty();
        metrics.recordNewShape("Test");
        metrics.recordEntityModeCheck();
        metrics.recordFieldModeCheck();
        metrics.recordInstanceStrategyCheck();
        metrics.recordValueStrategyCheck();
        metrics.recordCleanIdentityMatch();
        metrics.recordDirtyCacheMiss();

        metrics.reset();
        assertEquals(0, metrics.getChecks());
        assertEquals(0, metrics.getClean());
        assertEquals(0, metrics.getDirty());
        assertEquals(0, metrics.getIdentityMatches());
        assertEquals(0, metrics.getCacheMisses());
        assertEquals(0, metrics.getEntityModeChecks());
        assertEquals(0, metrics.getFieldModeChecks());
        assertEquals(0, metrics.getInstanceStrategyChecks());
        assertEquals(0, metrics.getValueStrategyChecks());
        assertEquals(0, metrics.getFieldComparisons());
        assertEquals(0, metrics.getFieldClean());
        assertEquals(0, metrics.getFieldDirty());
        assertEquals(0, metrics.getShapes());
    }

    // -----------------------------------------------------------------------
    // EntityCacheMetrics
    // -----------------------------------------------------------------------

    @Test
    public void testEntityCacheMetricsGetInstance() {
        var metrics = EntityCacheMetrics.getInstance();
        assertNotNull(metrics);
        assertTrue(metrics == EntityCacheMetrics.getInstance());
    }

    @Test
    public void testEntityCacheMetricsResetAndGetHit() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();
        assertEquals(0, metrics.getGets());
        assertEquals(0, metrics.getGetHits());
        assertEquals(0, metrics.getGetMisses());

        metrics.recordGetHit();
        assertEquals(1, metrics.getGets());
        assertEquals(1, metrics.getGetHits());
        assertEquals(0, metrics.getGetMisses());
    }

    @Test
    public void testEntityCacheMetricsGetMiss() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();

        metrics.recordGetMiss();
        assertEquals(1, metrics.getGets());
        assertEquals(0, metrics.getGetHits());
        assertEquals(1, metrics.getGetMisses());
    }

    @Test
    public void testEntityCacheMetricsGetHitRatio() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();
        // Zero division case.
        assertEquals(0, metrics.getGetHitRatioPercent());

        metrics.recordGetHit();
        metrics.recordGetHit();
        metrics.recordGetMiss();
        // 2 hits / 3 total = 66%.
        assertEquals(66, metrics.getGetHitRatioPercent());
    }

    @Test
    public void testEntityCacheMetricsInterns() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();

        metrics.recordInternHit();
        metrics.recordInternMiss();
        assertEquals(2, metrics.getInterns());
        assertEquals(1, metrics.getInternHits());
        assertEquals(1, metrics.getInternMisses());
    }

    @Test
    public void testEntityCacheMetricsInternHitRatio() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();
        // Zero division.
        assertEquals(0, metrics.getInternHitRatioPercent());

        metrics.recordInternHit();
        metrics.recordInternHit();
        metrics.recordInternHit();
        metrics.recordInternMiss();
        // 3/4 = 75%.
        assertEquals(75, metrics.getInternHitRatioPercent());
    }

    @Test
    public void testEntityCacheMetricsMutations() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.reset();

        metrics.recordRemoval();
        metrics.recordClear();
        metrics.recordEviction();
        assertEquals(1, metrics.getRemovals());
        assertEquals(1, metrics.getClears());
        assertEquals(1, metrics.getEvictions());
    }

    @Test
    public void testEntityCacheMetricsRegisterEntity() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.registerEntity("City", "STRONG");
        Map<String, String> retention = metrics.getRetentionPerEntity();
        assertEquals("STRONG", retention.get("City"));
    }

    @Test
    public void testEntityCacheMetricsResetClearsAll() {
        var metrics = EntityCacheMetrics.getInstance();
        metrics.recordGetHit();
        metrics.recordGetMiss();
        metrics.recordInternHit();
        metrics.recordInternMiss();
        metrics.recordRemoval();
        metrics.recordClear();
        metrics.recordEviction();

        metrics.reset();
        assertEquals(0, metrics.getGets());
        assertEquals(0, metrics.getGetHits());
        assertEquals(0, metrics.getGetMisses());
        assertEquals(0, metrics.getInterns());
        assertEquals(0, metrics.getInternHits());
        assertEquals(0, metrics.getInternMisses());
        assertEquals(0, metrics.getRemovals());
        assertEquals(0, metrics.getClears());
        assertEquals(0, metrics.getEvictions());
    }

    // -----------------------------------------------------------------------
    // TemplateMetrics
    // -----------------------------------------------------------------------

    @Test
    public void testTemplateMetricsGetInstance() {
        var metrics = TemplateMetrics.getInstance();
        assertNotNull(metrics);
        assertTrue(metrics == TemplateMetrics.getInstance());
    }

    @Test
    public void testTemplateMetricsResetAndBasicGetters() {
        var metrics = TemplateMetrics.getInstance();
        metrics.reset();
        assertEquals(0, metrics.getRequests());
        assertEquals(0, metrics.getHits());
        assertEquals(0, metrics.getMisses());
        assertEquals(0, metrics.getHitRatioPercent());
        assertEquals(0, metrics.getAvgRequestMicros());
        assertEquals(0, metrics.getMaxRequestMicros());
        assertEquals(0, metrics.getAvgHitMicros());
        assertEquals(0, metrics.getMaxHitMicros());
        assertEquals(0, metrics.getAvgMissMicros());
        assertEquals(0, metrics.getMaxMissMicros());
    }

    @Test
    public void testTemplateMetricsRegisterCacheSize() {
        var metrics = TemplateMetrics.getInstance();
        metrics.registerCacheSize(256);
        assertEquals(256, metrics.getTemplateCacheSize());
    }

    @Test
    public void testTemplateMetricsRecordHit() {
        var metrics = TemplateMetrics.getInstance();
        metrics.reset();

        try (var request = metrics.startRequest()) {
            request.hit();
        }
        assertEquals(1, metrics.getRequests());
        assertEquals(1, metrics.getHits());
        assertEquals(0, metrics.getMisses());
        assertTrue(metrics.getMaxRequestMicros() >= 0);
        assertTrue(metrics.getMaxHitMicros() >= 0);
    }

    @Test
    public void testTemplateMetricsRecordMiss() {
        var metrics = TemplateMetrics.getInstance();
        metrics.reset();

        try (var request = metrics.startRequest()) {
            request.miss();
        }
        assertEquals(1, metrics.getRequests());
        assertEquals(0, metrics.getHits());
        assertEquals(1, metrics.getMisses());
        assertTrue(metrics.getMaxMissMicros() >= 0);
    }

    @Test
    public void testTemplateMetricsUnmarkedOutcomeTreatedAsMiss() {
        var metrics = TemplateMetrics.getInstance();
        metrics.reset();

        // Close without calling hit() or miss().
        try (var request = metrics.startRequest()) {
            // Intentionally not calling hit() or miss().
        }
        assertEquals(1, metrics.getRequests());
        // Unmarked outcome treated as MISS.
        assertEquals(0, metrics.getHits());
        assertEquals(1, metrics.getMisses());
    }

    @Test
    public void testTemplateMetricsHitRatio() {
        var metrics = TemplateMetrics.getInstance();
        metrics.reset();

        // 3 hits, 1 miss = 75%.
        for (int i = 0; i < 3; i++) {
            try (var request = metrics.startRequest()) {
                request.hit();
            }
        }
        try (var request = metrics.startRequest()) {
            request.miss();
        }
        assertEquals(75, metrics.getHitRatioPercent());
        assertTrue(metrics.getAvgRequestMicros() >= 0);
        assertTrue(metrics.getAvgHitMicros() >= 0);
        assertTrue(metrics.getAvgMissMicros() >= 0);
    }

    @Test
    public void testTemplateMetricsResetClearsAll() {
        var metrics = TemplateMetrics.getInstance();
        try (var request = metrics.startRequest()) {
            request.hit();
        }
        try (var request = metrics.startRequest()) {
            request.miss();
        }

        metrics.reset();
        assertEquals(0, metrics.getRequests());
        assertEquals(0, metrics.getHits());
        assertEquals(0, metrics.getMisses());
        assertEquals(0, metrics.getMaxRequestMicros());
        assertEquals(0, metrics.getMaxHitMicros());
        assertEquals(0, metrics.getMaxMissMicros());
    }
}
