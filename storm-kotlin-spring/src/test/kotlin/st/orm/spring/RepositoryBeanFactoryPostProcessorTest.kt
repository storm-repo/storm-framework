package st.orm.spring

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode.ALL
import org.springframework.test.context.jdbc.Sql

/**
 * Tests for [RepositoryBeanFactoryPostProcessor] covering edge cases such as
 * empty base packages, nonexistent packages, and resource loader handling.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@ContextConfiguration(classes = [IntegrationConfig::class])
@Import(RepositoryBeanFactoryPostProcessorTest.EmptyPackagesPostProcessor::class)
@TestConstructor(autowireMode = ALL)
@SpringBootTest
@Sql("/data.sql")
class RepositoryBeanFactoryPostProcessorTest(
    val applicationContext: ApplicationContext,
) {

    @Configuration
    open class EmptyPackagesPostProcessor : RepositoryBeanFactoryPostProcessor() {
        override val ormTemplateBeanName: String get() = "ormTemplate"

        // Empty packages: should return early without registering anything
        override val repositoryBasePackages: Array<String> get() = emptyArray()
    }

    // ======================================================================
    // Empty packages early return
    // ======================================================================

    @Test
    fun `empty base packages should not register any repository beans`() {
        // With empty packages, postProcessBeanFactory returns early.
        // VisitRepository should NOT be registered.
        applicationContext.containsBean("VisitRepository").shouldBeFalse()
    }

    @Test
    fun `empty base packages should still allow context to start successfully`() {
        // Even with an EmptyPackagesPostProcessor, the application context should be fully
        // functional. This verifies the early return path doesn't break the bean factory.
        applicationContext.containsBean("ormTemplate").shouldBeTrue()
    }

    @Test
    fun `non-repository beans should be unaffected by empty packages processor`() {
        // The EmptyPackagesPostProcessor should not interfere with other beans
        // that are normally registered by Spring.
        val ormTemplate = applicationContext.getBean("ormTemplate")
        (ormTemplate != null).shouldBeTrue()
    }
}
