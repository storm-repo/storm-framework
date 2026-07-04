package st.orm.ktor.koin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import st.orm.ktor.Storm
import st.orm.ktor.koin.model.Pet
import st.orm.ktor.koin.model.PetRepository
import st.orm.template.ORMTemplate

/** A service wired by Koin's constructor DSL from a Storm repository. */
class PetService(private val petRepository: PetRepository) {
    fun countPets() = petRepository.findAll().size
}

class StormKoinTest {

    private fun createTestDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:storm-koin-test-${System.nanoTime()};DB_CLOSE_DELAY=-1"
            driverClassName = "org.h2.Driver"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        }
        return HikariDataSource(config)
    }

    private fun initializeSchema(dataSource: HikariDataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val sql = this::class.java.getResourceAsStream("/schema.sql")!!.bufferedReader().readText()
                for (line in sql.split(";")) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        statement.execute(trimmed)
                    }
                }
            }
        }
    }

    @Test
    fun `stormModule exposes orm and repositories to koin`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    install(Koin) {
                        modules(stormModule())
                    }
                    val koin = getKoin()
                    koin.get<ORMTemplate>() shouldNotBe null
                    koin.get<PetRepository>().findAll().size shouldBe 3
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `services declared with constructor DSL receive storm repositories`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    install(Koin) {
                        modules(
                            stormModule(),
                            module {
                                singleOf(::PetService)
                            },
                        )
                    }
                    getKoin().get<PetService>().countPets() shouldBe 3
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `entity repositories resolve alongside custom repositories`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    install(Koin) {
                        modules(stormModule())
                    }
                    // The ORMTemplate itself is available for direct entity access.
                    val orm = getKoin().get<ORMTemplate>()
                    orm.entity(Pet::class).findAll().size shouldBe 3
                }
            }
        } finally {
            dataSource.close()
        }
    }
}
