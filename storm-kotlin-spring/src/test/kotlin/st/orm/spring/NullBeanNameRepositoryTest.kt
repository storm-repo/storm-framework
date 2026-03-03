package st.orm.spring

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode.ALL
import org.springframework.test.context.jdbc.Sql
import st.orm.spring.repository.VisitRepository

/**
 * Tests for [RepositoryBeanFactoryPostProcessor] with null [ormTemplateBeanName].
 * This exercises the getBeanORMTemplate branch where beanName is null,
 * falling back to getBean(ORMTemplate::class.java) by type.
 */
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@ContextConfiguration(classes = [IntegrationConfig::class])
@Import(NullBeanNameRepositoryTest.NullBeanNamePostProcessor::class)
@TestConstructor(autowireMode = ALL)
@SpringBootTest
@Sql("/data.sql")
class NullBeanNameRepositoryTest(
    val visitRepository: VisitRepository,
) {

    @Configuration
    open class NullBeanNamePostProcessor : RepositoryBeanFactoryPostProcessor() {
        // ormTemplateBeanName is null (default), so getBeanORMTemplate
        // will use beanFactory.getBean(ORMTemplate::class.java) by type lookup.
        override val repositoryBasePackages: Array<String> get() = arrayOf("st.orm.spring.repository")
    }

    @Test
    fun `repository with null ormTemplateBeanName should resolve by type`() {
        visitRepository.count() shouldBe 14
    }
}
