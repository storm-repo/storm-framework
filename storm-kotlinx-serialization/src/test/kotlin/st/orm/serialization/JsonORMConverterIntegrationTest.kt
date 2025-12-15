package st.orm.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.*
import st.orm.core.template.SqlInterceptor
import st.orm.core.template.SqlTemplateException
import st.orm.serialization.model.*
import st.orm.template.ORMTemplate
import st.orm.template.Templates.alias
import javax.sql.DataSource
import kotlin.test.assertEquals

@Suppress("OPT_IN_USAGE")
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonORMConverterIntegrationTest(@Autowired val dataSource: DataSource) {

    @Test
    fun testSelectOwner() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT id, first_name, last_name, address, telephone FROM owner")
        val owner = query.getResultList(Owner::class)
        Assertions.assertEquals(10, owner.stream().distinct().count())
    }

    @Test
    fun testSelectRef() {
        data class Result(@Json val owner: List<Ref<Owner>>)
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT JSON_ARRAYAGG(id) FROM owner")
        val owner = query.getSingleResult(Result::class).owner.stream()
            .map { it.id() }.distinct().toList()
        Assertions.assertEquals(10, owner.size)
    }

    @Test
    fun testInsertOwner() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(Owner::class)
        val address = Address("271 University Ave", "Palo Alto")
        val owner = Owner(
            firstName = "Simon",
            lastName = "McDonald",
            address = address,
            telephone = "555-555-5555"
        )
        val inserted = repository.insertAndFetch(owner)
        assertEquals(address, inserted.address)
    }

    @Test
    fun testUpdateOwner() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(Owner::class)
        val owner = repository.getById(1)
        val address = Address("271 University Ave", "Palo Alto")
        repository.update(owner.copy(address = address))
        val updated = repository.getById(1)
        assertEquals(address, updated.address)
    }

    @Serializable
    data class Person(val firstName: String, val lastName: String)

    @DbTable("owner")
    data class OwnerWithJsonPerson(
        @PK val id: Int,
        @Json val person: Person,
        @Json val address: Address,
        val telephone: String?
    ) : Entity<Int>


    @Test
    fun testOwnerWithJsonPerson() {
        val orm = ORMTemplate.of(dataSource)
        val query =
            orm.query("SELECT id, JSON_OBJECT('firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner")
        val owner = query.getResultList(OwnerWithJsonPerson::class)
        Assertions.assertEquals(10, owner.size)
    }

    @DbTable("owner")
    data class OwnerWithJsonMapAddress(
        @PK val id: Int,
        val firstName: String,
        val lastName: String,
        @Json val address: Map<String, String>,
        val telephone: String?
    ) : Entity<Int>

    @Test
    fun testOwnerWithJsonMapAddress() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(OwnerWithJsonMapAddress::class)
        val owner = repository.select().resultList
        Assertions.assertEquals(10, owner.size)
    }

    @DbTable("owner")
    data class OwnerWithInlineJsonMapAddress(
        @PK val id: Int,
        val firstName: String,
        val lastName: String,
        @Json @Inline val address: Map<String, String>,
        val telephone: String?
    ) : Entity<Int>

    @Test
    fun testOwnerWithInlineJsonMapAddress() {
        val e = Assertions.assertThrows(PersistenceException::class.java) {
            val orm = ORMTemplate.of(dataSource)
            val repository = orm.entity(OwnerWithInlineJsonMapAddress::class)
            repository.select().resultList
        }
        Assertions.assertInstanceOf(SqlTemplateException::class.java, e.cause)
    }

    data class SpecialtiesByVet(
        val vet: Vet,
        @Json val specialties: List<Specialty>  // Note that Specialty has been made @Serializable.
    )

    @Test
    fun testSpecialtiesByVet() {
        val vets = ORMTemplate.of(dataSource)
            .selectFrom(Vet::class, SpecialtiesByVet::class) {
                """
                    ${t(Vet::class)}, JSON_ARRAYAGG(
                        JSON_OBJECT(
                            KEY 'id' VALUE ${t(alias(Specialty::class))}.id,
                            KEY 'name' VALUE ${t(alias(Specialty::class))}.name
                        )
                    ) AS specialties
                """.trimIndent()
            }
            .innerJoin(VetSpecialty::class).on(Vet::class)
            .innerJoin(Specialty::class).on(VetSpecialty::class)
            .groupBy { "${t(Vet::class)}.id" }
            .resultList
        Assertions.assertEquals(4, vets.size)
        Assertions.assertEquals(5, vets.sumOf { it.specialties.size })
    }

    internal data class SpecialtyNamesByVet(
        val vet: Vet,
        @Json val specialties: List<String>
    )

    @Test
    fun testSpecialtyNamesByVet() {
        val vets = ORMTemplate.of(dataSource).selectFrom(Vet::class, SpecialtyNamesByVet::class) {
            """
                ${t(Vet::class)}, JSON_ARRAYAGG(${t(Specialty::class)}.name) AS specialties
            """.trimIndent()
        }
            .innerJoin(VetSpecialty::class).on(Vet::class)
            .innerJoin(Specialty::class).on(VetSpecialty::class)
            .groupBy { "${t(Vet::class)}.id" }
            .resultList
        Assertions.assertEquals(4, vets.size)
        Assertions.assertEquals(5, vets.sumOf { it.specialties.size })
    }

    @Test
    fun testSpecialtiesByVetDoubleClass() {
        val expectedSql = """
                SELECT v.id, v.first_name, v.last_name, JSON_OBJECTAGG(s.id, s.name)
                FROM vet v
                INNER JOIN vet_specialty vs ON vs.vet_id = v.id
                INNER JOIN specialty s ON vs.specialty_id = s.id
                GROUP BY v.id
                """.trimIndent()
        SqlInterceptor.observe(
            { sql -> Assertions.assertEquals(expectedSql, sql!!.statement()) },
            {
                try {
                    ORMTemplate.of(dataSource).selectFrom(Vet::class, SpecialtyNamesByVet::class) {
                        """
                            ${t(Vet::class)}, JSON_OBJECTAGG(${t(Specialty::class)})
                        """.trimIndent()
                    }
                        .innerJoin(VetSpecialty::class).on(Vet::class)
                        .innerJoin(Specialty::class).on(VetSpecialty::class)
                        .groupBy { "${t(Vet::class)}.id" }
                        .resultList
                } catch (ignore: PersistenceException) {
                    // H2 Does not support JSON_OBJECTAGG. We only check the expected SQL.
                }
            })
    }

    @JsonClassDiscriminator("@type")
    @Serializable
    sealed interface PolymorphicPerson

    // The type name is automatically derived from the class name, so the annotation is not needed.
    @SerialName("PersonA")
    @Serializable
    data class PersonA(val firstName: String, val lastName: String) : PolymorphicPerson

    @SerialName("PersonB")
    @Serializable
    data class PersonB(val firstName: String, val lastName: String) : PolymorphicPerson

    @DbTable("owner")
    data class OwnerWithPolymorphicPerson(
        @PK val id: Int,
        @Json val person: PolymorphicPerson,
        @Json val address: Address,
        val telephone: String?
    ) : Entity<Int>

    @Test
    fun testPolymorphic() {
        val orm = ORMTemplate.of(dataSource)
        val query =
            orm.query("SELECT id, JSON_OBJECT('@type' VALUE 'PersonA', 'firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner")
        val owner = query.getResultList(OwnerWithPolymorphicPerson::class)
        Assertions.assertEquals(10, owner.size)
        Assertions.assertTrue(owner.stream().allMatch { x: OwnerWithPolymorphicPerson? -> x!!.person is PersonA })
    }
}