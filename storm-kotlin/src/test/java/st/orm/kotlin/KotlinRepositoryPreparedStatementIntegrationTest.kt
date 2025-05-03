package st.orm.kotlin;

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.kotlin.KTemplates.ORM
import st.orm.kotlin.KTemplates.alias
import st.orm.kotlin.model.KotlinPet
import st.orm.kotlin.model.Pet
import st.orm.kotlin.model.Visit
import st.orm.kotlin.repository.KotlinPetRepository
import st.orm.template.ResolveScope.INNER
import st.orm.template.ResolveScope.OUTER
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class KotlinRepositoryPreparedStatementIntegrationTest {

    @Autowired
    private val dataSource: DataSource? = null

    @Test
    fun testWithArg() {
        val list = ORM(dataSource).entity(Pet::class)
            .select()
            .where { it { "${it(Pet::class)}.id = 7" } }
            .resultList
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithTwoArgs() {
        val list = ORM(dataSource).entity(Pet::class)
            .select()
            .where { it { "${it(Pet::class)}.id = 7 OR ${it(Pet::class)}.id = 8" } }
            .resultList
        assertEquals(2, list.size)
        assertEquals(7, list[0].id)
        assertEquals(8, list[1].id)
    }

    @Test
    fun testBuilderWithAutoJoin() {
        val list = ORM(dataSource).entity(Pet::class)
            .select()
            .innerJoin(Visit::class).on(Pet::class)
            .where { it.whereAny(Visit(1, null, null, null)) }
            .resultList
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun testWithKotlinDataClass() {
        val list = ORM(dataSource).entity(KotlinPet::class)
            .select()
            .resultList
        assertEquals(13, list.size)
    }

    @Test
    fun testSelectAll() {
        val repository = ORM(dataSource).repository(KotlinPetRepository::class)
        assertEquals(13, repository.select().resultList.size)
    }

    @Test
    fun testFindAll() {
        val repository = ORM(dataSource).repository(KotlinPetRepository::class)
        repository.getAll().let {
            assertEquals(1, it.first().id())
            assertEquals(13, it.size)
        }
    }

    @Test
    fun testExists() {
        val repository = ORM(dataSource).entity(Pet::class)
        repository.select()
            .where { it.exists(it.subquery(Pet::class).where { it { "${it(alias(Pet::class, OUTER))}.id <> ${it(alias(Pet::class, INNER))}.id"} }) }
            .resultList.let {
                assertEquals(13, it.size)
            }
    }

    @Test
    fun testNotExists() {
        val repository = ORM(dataSource).entity(Pet::class)
        repository.select()
            .where { it.notExists(it.subquery(Pet::class).where { it { "${it(alias(Pet::class, OUTER))}.id <> ${it(alias(Pet::class, INNER))}.id"} }) }
            .resultList.let {
                assertEquals(0, it.size)
            }
    }
}