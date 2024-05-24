package st.orm.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.kotlin.IntegrationConfig
import st.orm.kotlin.KTemplates.ORM
import st.orm.kotlin.model.KotlinPet
import st.orm.kotlin.model.Pet
import st.orm.kotlin.model.Visit
import st.orm.kotlin.repository.KotlinPetRepository
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class KotlinRepositoryPreparedStatementIntegrationTest {

    @Autowired
    private val dataSource: DataSource? = null

    @Test
    fun testWithArg() {
        val ORM = ORM(dataSource)
        val list = ORM.repository(Pet::class)
                .withTemplate {
                    "WHERE ${it.arg(Pet::class)}.id = 7"
                }
                .toList()
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithTwoArgs() {
        val ORM = ORM(dataSource)
        val list = ORM.repository(Pet::class)
                .withTemplate {
                    "WHERE ${it.arg(Pet::class)}.id = 7 OR ${it.arg(Pet::class)}.id = 8"
                }
                .toList()
        assertEquals(2, list.size)
        assertEquals(7, list[0].id)
        assertEquals(8, list[1].id)
    }

    @Test
    fun testBuilderWithAutoJoin() {
        val ORM = ORM(dataSource)
        val list = ORM.repository(Pet::class)
                .innerJoin(Visit::class).on(Pet::class)
                .where(Visit(1, null, null, null))
                .toList()
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithKotlinDataClass() {
        val ORM = ORM(dataSource)
        val list = ORM.repository(KotlinPet::class).toList()
        assertEquals(13, list.size)
    }

    @Test
    fun testSelectAll() {
        val repository = ORM(dataSource).repositoryProxy(KotlinPetRepository::class)
        repository.selectAll().let {
            assertEquals(13, it.count())
        }
    }

    @Test
    fun testFindAll() {
        val repository = ORM(dataSource).repositoryProxy(KotlinPetRepository::class)
        repository.findAll().let {
            assertEquals(1, it.first().id())
            assertEquals(13, it.size)
        }
    }
}