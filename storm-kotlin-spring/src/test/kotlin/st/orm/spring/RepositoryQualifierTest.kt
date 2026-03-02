package st.orm.spring

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode.ALL
import org.springframework.test.context.jdbc.Sql
import st.orm.spring.repository.VisitRepository

/**
 * Tests for [RepositoryBeanFactoryPostProcessor] with repository prefix (qualifier) support
 * and the [RepositoryBeanFactoryPostProcessor.RepositoryAutowireCandidateResolver].
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@ContextConfiguration(classes = [IntegrationConfig::class])
@Import(
    RepositoryQualifierTest.PrefixedRepositoryPostProcessor::class,
    RepositoryQualifierTest.DefaultRepositoryPostProcessor::class,
)
@TestConstructor(autowireMode = ALL)
@SpringBootTest
@Sql("/data.sql")
class RepositoryQualifierTest(
    val applicationContext: ApplicationContext,
    @Qualifier("prefixed_") val prefixedVisitRepository: VisitRepository,
) {

    @Configuration
    open class PrefixedRepositoryPostProcessor : RepositoryBeanFactoryPostProcessor() {
        override val ormTemplateBeanName: String get() = "ormTemplate"
        override val repositoryBasePackages: Array<String> get() = arrayOf("st.orm.spring.repository")
        override val repositoryPrefix: String get() = "prefixed_"
    }

    @Configuration
    open class DefaultRepositoryPostProcessor : RepositoryBeanFactoryPostProcessor() {
        override val ormTemplateBeanName: String get() = "ormTemplate"
        override val repositoryBasePackages: Array<String> get() = arrayOf("st.orm.spring.repository")
    }

    // ======================================================================
    // Prefixed repository registration
    // ======================================================================

    @Test
    fun `prefixed repository should be registered with prefix in bean name`() {
        val bean = applicationContext.getBean("prefixed_VisitRepository")
        bean.shouldNotBeNull()
        (bean is VisitRepository) shouldBe true
    }

    @Test
    fun `prefixed repository should be functional`() {
        prefixedVisitRepository.count() shouldBe 14
    }

    @Test
    fun `default repository should also be registered`() {
        val bean = applicationContext.getBean("VisitRepository")
        bean.shouldNotBeNull()
        (bean is VisitRepository) shouldBe true
    }

    @Test
    fun `qualifier-injected bean should be the same instance as the prefixed bean from context`() {
        // The @Qualifier("prefixed_") injection should resolve to the exact same bean
        // registered under the "prefixed_VisitRepository" name.
        val prefixedFromContext = applicationContext.getBean("prefixed_VisitRepository") as VisitRepository
        (prefixedVisitRepository === prefixedFromContext) shouldBe true
    }

    @Test
    fun `prefixed and default beans should be distinct instances`() {
        val prefixed = applicationContext.getBean("prefixed_VisitRepository") as VisitRepository
        val defaultBean = applicationContext.getBean("VisitRepository") as VisitRepository
        // They should be different proxy instances (different RepositoryBeanFactoryPostProcessors).
        (prefixed !== defaultBean) shouldBe true
        // But both should produce correct results.
        prefixed.count() shouldBe 14
        defaultBean.count() shouldBe 14
    }
}
