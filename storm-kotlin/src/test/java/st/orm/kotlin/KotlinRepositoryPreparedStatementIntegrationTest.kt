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
        val list = ORM(dataSource).entityRepository(Pet::class)
            .select()
            .where { "${it(Pet::class)}.id = 7" }
            .resultList
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithTwoArgs() {
        val list = ORM(dataSource).entityRepository(Pet::class)
            .select()
            .where { "${it(Pet::class)}.id = 7 OR ${it(Pet::class)}.id = 8" }
            .resultList
        assertEquals(2, list.size)
        assertEquals(7, list[0].id)
        assertEquals(8, list[1].id)
    }

    @Test
    fun testBuilderWithAutoJoin() {
        val list = ORM(dataSource).entityRepository(Pet::class)
            .select()
            .innerJoin(Visit::class).on(Pet::class)
            .where(Visit(1, null, null, null))
            .resultList
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithKotlinDataClass() {
        val list = ORM(dataSource).entityRepository(KotlinPet::class)
            .select()
            .resultList
        assertEquals(13, list.size)
    }

    @Test
    fun testSelectAll() {
        val repository = ORM(dataSource).repositoryProxy(KotlinPetRepository::class)
        assertEquals(13, repository.selectAll { it.count() })
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