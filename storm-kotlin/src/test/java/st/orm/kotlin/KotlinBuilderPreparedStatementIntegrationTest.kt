package st.orm.kotlin

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.kotlin.KTemplates.ORM
import st.orm.kotlin.model.Pet
import st.orm.kotlin.model.Vet
import st.orm.kotlin.model.Visit
import java.time.LocalDate
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class KotlinBuilderPreparedStatementIntegrationTest {

    @Autowired
    private val dataSource: DataSource? = null

    @Test
    fun testBuilderWithJoin() {
        val list = ORM(dataSource)
            .selectFrom(Pet::class)
            .innerJoin(Visit::class).on { "${it(Pet::class)}.id = ${it(Visit::class)}.pet_id" }
            .where { it { "${it(Visit::class)}.visit_date = ${it(LocalDate.of(2023, 1, 8))}" } }
            .resultList
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithJoinParameter() {
        val list = ORM(dataSource)
            .selectFrom(Pet::class)
            .innerJoin(Visit::class).on { "${it(Pet::class)}.id = ${it(1)}" }
            .where { it { "${it(Visit::class)}.visit_date = ${it(LocalDate.of(2023, 1, 8))}" } }
            .resultList
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithWhere() {
        val list = ORM(dataSource)
            .selectFrom(Vet::class)
            .where { it.filterId(1).or(it.filterId(2)) }
            .resultList
        Assertions.assertEquals(2, list.size)
    }

    @Test
    fun testBuilderWithWhereExpression() {
        val list = ORM(dataSource)
            .selectFrom(Vet::class)
            .where { it { "1 = 1" } }
            .resultList
        Assertions.assertEquals(6, list.size)
    }

    @Test
    fun testBuilderWithWhereTemplateFunction() {
        val list = ORM(dataSource)
            .selectFrom(Vet::class)
            .where { it.filter { "1 = 1" } }
            .resultList
        Assertions.assertEquals(6, list.size)
    }

    @Test
    fun testBuilderWithWhereTemplateFunctionAfterOr() {
        val list = ORM(dataSource)
            .selectFrom(Vet::class)
            .where { it.filterId(1).or(it.filter {"${it(Vet::class)}.id = ${it(2)}"}) }
            .resultList
        Assertions.assertEquals(2, list.size)
    }
}