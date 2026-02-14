package st.orm.spring

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode.ALL
import org.springframework.test.context.jdbc.Sql
import st.orm.spring.repository.OwnerRepository
import st.orm.spring.repository.VisitRepository

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@ContextConfiguration(classes = [IntegrationConfig::class])
@Import(TestRepositoryBeanFactoryPostProcessor::class)
@TestConstructor(autowireMode = ALL)
@SpringBootTest
@Sql("/data.sql")
class RepositoryTest(
    val visitRepository: VisitRepository,
    val ownerRepositoryTest: OwnerRepository?
) {

    @Test
    fun `visit repository should be autowired and return count matching test data`() {
        // VisitRepository is a standard EntityRepository registered via TestRepositoryBeanFactoryPostProcessor.
        // The test data contains 14 visit rows, so count should return 14.
        visitRepository.count() shouldBe 14
    }

    @Test
    fun `owner repository should not be autowired due to NoRepositoryBean annotation`() {
        // OwnerRepository is annotated with @NoRepositoryBean, so the RepositoryBeanFactoryPostProcessor
        // should skip it during scanning. It should not be registered as a Spring bean.
        ownerRepositoryTest shouldBe null
    }
}