package st.orm.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import st.orm.ktor.model.PetRepository
import st.orm.ktor.vet.Vet
import st.orm.ktor.vet.VetRepository
import st.orm.template.ORMTemplate

/**
 * Verifies the named-database support of the [Storm] plugin: per-database templates and repositories, package
 * partitioning of registration and schema validation, named accessors, and dependency injection.
 *
 * The scenario follows the pet clinic domain: the clinic's own database holds the pets, while the vet registry
 * lives in a separate, named database.
 */
class StormMultipleDatabasesTest {

    private fun createTestDataSource(name: String, schemaResource: String): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:$name-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        }
        val dataSource = HikariDataSource(config)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val sql = this::class.java.getResourceAsStream(schemaResource)!!.bufferedReader().readText()
                for (line in sql.split(";")) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        statement.execute(trimmed)
                    }
                }
            }
        }
        return dataSource
    }

    private fun withClinicAndVetsDatabases(block: io.ktor.server.application.Application.() -> Unit) {
        val clinicDataSource = createTestDataSource("storm-multi-clinic", "/schema.sql")
        val vetsDataSource = createTestDataSource("storm-multi-vets", "/schema-vets.sql")
        try {
            testApplication {
                application {
                    install(Storm) {
                        dataSource = clinicDataSource
                        // The primary must not see the vet package; partitioning also keeps schema
                        // validation (mode "fail" by default) green for both databases.
                        database("vets") {
                            dataSource = vetsDataSource
                            repositories("st.orm.ktor.vet")
                        }
                    }
                    block()
                }
            }
        } finally {
            clinicDataSource.close()
            vetsDataSource.close()
        }
    }

    @Test
    fun `each database gets its own template and repositories`() = withClinicAndVetsDatabases {
        val vetsDatabase = orm("vets")
        vetsDatabase shouldNotBe orm
        val vets = repository<VetRepository>("vets")
        vets.findAll().size shouldBe 2
        // The clinic's own database keeps working against its own schema.
        repository<PetRepository>().findAll().shouldNotBeEmpty()
    }

    @Test
    fun `repositories of a claimed package are not served by the primary database`() = withClinicAndVetsDatabases {
        val exception = shouldThrow<IllegalStateException> {
            repository<VetRepository>()
        }
        exception.message!! shouldContain "vets"
    }

    @Test
    fun `named template and repositories are resolvable through dependency injection`() = withClinicAndVetsDatabases {
        // The primary template resolves unnamed; each named database's template resolves under its name.
        val primary = runBlocking { dependencies.resolve<ORMTemplate>() }
        primary shouldBeSameInstanceAs orm
        val vetsDatabase = runBlocking { dependencies.resolve<ORMTemplate>("vets") }
        vetsDatabase shouldBeSameInstanceAs orm("vets")
        // Repositories are registered unnamed under their own type, regardless of database.
        val vets: VetRepository by dependencies
        vets shouldBeSameInstanceAs repository<VetRepository>("vets")
        val pets: PetRepository by dependencies
        pets shouldBeSameInstanceAs repository<PetRepository>()
    }

    @Test
    fun `writes go to the right database`() = withClinicAndVetsDatabases {
        val vets = repository<VetRepository>("vets")
        val insertedVet = vets.insertAndFetch(Vet(firstName = "Sharon", lastName = "Jenkins"))
        insertedVet.id shouldNotBe 0
        vets.findAll().size shouldBe 3
        // The clinic database has no vet rows; its repositories are untouched.
        repository<PetRepository>().findAll().size shouldBe 3
    }

    @Test
    fun `unknown database name fails with a descriptive error`() = withClinicAndVetsDatabases {
        val exception = shouldThrow<IllegalStateException> {
            orm("specialties")
        }
        exception.message!! shouldContain "No database named 'specialties'"
        exception.message!! shouldContain "vets"
    }
}
