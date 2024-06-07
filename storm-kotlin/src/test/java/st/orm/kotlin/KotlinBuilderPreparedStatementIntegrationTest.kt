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
                    "${it(Pet::class)}.id = ${it(Visit::class)}.pet_id"
                }
                .stream {
                    "WHERE ${it(Visit::class)}.visit_date = ${it(LocalDate.of(2023, 1, 8))}"
                }
                .toList()
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithJoinParameter() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Pet::class)
                .innerJoin(Visit::class).on().template {
                    "${it(Pet::class)}.id = ${it(1)}"
                }
                .stream {
                    "WHERE ${it(Visit::class)}.visit_date = ${it(LocalDate.of(2023, 1, 8))}"
                }
                .toList()
        Assertions.assertEquals(3, list.size)
    }

    @Test
    fun testBuilderWithWhere() {
        val ORM = ORM(dataSource)
        val list = ORM.query(Vet::class)
                .where {
                    it.filter(1).or(it.filter(2))
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
                    it.filter(1).or(
                        it.template {
                            "${it(Vet::class)}.id = ${it(2)}"
                        })
                }
                .toList()
        Assertions.assertEquals(2, list.size)
    }

}