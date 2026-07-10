package st.orm.core.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import st.orm.core.spi.Providers;

/**
 * Verifies that the test-only Spring-aware providers are resolved through the {@code ServiceLoader} fallback when
 * running under the test harness. The storm-core test suite depends on these providers for transaction-per-test
 * isolation.
 */
public class ProviderResolutionTest {

    @Test
    public void testConnectionProviderResolvesToTestProvider() {
        assertEquals(TestSpringConnectionProvider.class, Providers.getConnectionProvider().getClass());
    }

    @Test
    public void testTransactionTemplateProviderResolvesToTestProvider() {
        assertEquals(TestSpringTransactionTemplateProvider.class,
                Providers.getTransactionTemplateProvider().getClass());
    }
}
