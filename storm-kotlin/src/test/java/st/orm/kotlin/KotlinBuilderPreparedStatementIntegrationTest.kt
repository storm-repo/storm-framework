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
        val ORM = ORM(dataSource)
        val list = ORM.query(Pet::class)
                .innerJoin(Visit::class).on().template {
                    "${it.arg(Pet::class)}.id = ${it.arg(Visit::class)}.pet_id"
                }
                .stream {
                    "WHERE ${it.arg(Visit::class)}.visit_date = ${it.arg(LocalDate.of(2023, 1, 8))}"
                }
                .toList()
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithJoinParameter() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Pet::class)
                .innerJoin(Visit::class).on().template {
                    "${it.arg(Pet::class)}.id = ${it.arg(1)}"
                }
                .stream {
                    "WHERE ${it.arg(Visit::class)}.visit_date = ${it.arg(LocalDate.of(2023, 1, 8))}"
                }
                .toList()
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithWhere() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Vet::class)
                .where {
                    it.matches(1).or(it.matches(2))
                }
                .toList()
        Assertions.assertEquals(2, list.size)
    }

    @Test
    fun testBuilderWithWhereTemplateFunction() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Vet::class)
                .where { group  -> group.template { "1 = 1" } }
                .toList()
        Assertions.assertEquals(6, list.size)
    }

    @Test
    fun testBuilderWithWhereTemplateFunctionAfterOr() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Vet::class)
                .where {
                    it.matches(1).or(
                        it.template {
                            "${it.arg(Vet::class)}.id = ${it.arg(2)}"
                        })
                }
                .toList()
        Assertions.assertEquals(2, list.size)
    }

}