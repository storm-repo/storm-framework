package st.orm.kotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.kotlin.KTemplates.ORM
import st.orm.kotlin.model.Owner
import st.orm.kotlin.model.Pet
import st.orm.kotlin.model.PetType
import java.util.*
import javax.sql.DataSource

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class KotlinTemplatePreparedStatementIntegrationTest {

    @Autowired
    private val dataSource: DataSource? = null

    @Test
    fun testSelectPet() {
        val stream = ORM(dataSource).template {
            """
            SELECT ${it(Pet::class)}
            FROM ${it(Pet::class)}
            """.trimIndent()
        }.getResultStream(Pet::class)
        assert(10L == stream.filter { it != null }
                .map { it.owner }
                .filter { it != null }
                .map { it.firstName }
                .distinct()
                .count())
    }

    @Test
    fun testSelectPetWithJoins() {
        val nameFilter = "%y%"
        val orm = ORM(dataSource)
        val stream = orm.template {
            """
            SELECT ${it(orm.s(Pet::class))}
            FROM ${it(orm.t(Pet::class, "p"))}
            INNER JOIN ${it(orm.t(PetType::class, "pt"))} ON p.type_id = pt.id
            LEFT OUTER JOIN ${it(orm.t(Owner::class, "o"))} ON p.owner_id = o.id
            WHERE p.name LIKE ${it(orm.p(nameFilter))}
            """.trimIndent()
        }.getResultStream(Pet::class)
        assertEquals(5, stream.filter(Objects::nonNull)
                .map(Pet::owner)
                .filter(Objects::nonNull)
                .map(Owner::firstName)
                .distinct()
                .count())
    }
}