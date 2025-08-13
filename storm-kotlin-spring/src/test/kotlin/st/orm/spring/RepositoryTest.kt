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
    fun `visit repository wired`() {
        visitRepository.count() shouldBe 14
    }

    @Test
    fun `owner repository should not be wired`() {
        ownerRepositoryTest shouldBe null
    }
}