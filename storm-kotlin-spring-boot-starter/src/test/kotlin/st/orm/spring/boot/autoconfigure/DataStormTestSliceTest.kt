package st.orm.spring.boot.autoconfigure

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import st.orm.spring.boot.autoconfigure.slice.Visit
import st.orm.spring.boot.autoconfigure.slice.VisitRepository
import st.orm.spring.boot.test.DataStormTest

/**
 * Verifies that the shared @DataStormTest slice works against the Kotlin starter: the slice imports resolve
 * to the Kotlin starter's auto-configuration classes, repositories bind through the Kotlin adapter, and each
 * test rolls back.
 */
@TestMethodOrder(OrderAnnotation::class)
@DataStormTest
class DataStormTestSliceTest(
    @Autowired private val visitRepository: VisitRepository,
    @Autowired private val applicationContext: ApplicationContext,
) {

    @Test
    @Order(1)
    fun `repositories are scanned through the Kotlin adapter`() {
        visitRepository.count() shouldBe 2
        applicationContext.getBean(st.orm.spring.AbstractRepositoryBeanFactoryPostProcessor::class.java)
            .shouldBeInstanceOf<AutoConfiguredRepositoryBeanFactoryPostProcessor>()
    }

    @Test
    @Order(2)
    fun `writes are rolled back per test`() {
        visitRepository.insert(Visit(description = "written inside the test transaction"))
        visitRepository.count() shouldBe 3
    }

    @Test
    @Order(3)
    fun `previous test's write is gone`() {
        visitRepository.count() shouldBe 2
    }
}
