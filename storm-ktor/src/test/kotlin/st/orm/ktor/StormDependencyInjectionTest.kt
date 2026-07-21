package st.orm.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import org.junit.jupiter.api.Test
import st.orm.ktor.model.PetRepository
import st.orm.template.ORMTemplate
import kotlin.reflect.full.starProjectedType

/**
 * Verifies that the Storm plugin exposes the [ORMTemplate] and the registered repositories through Ktor's
 * dependency injection (`ktor-server-di`), and that the exposure can be disabled.
 */
class StormDependencyInjectionTest {

    private fun createTestDataSource(): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:storm-di-test-${System.nanoTime()};DB_CLOSE_DELAY=-1"
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
    fun `ORMTemplate is resolvable through dependency injection`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    val injectedOrm: ORMTemplate by dependencies
                    injectedOrm.shouldNotBeNull()
                    injectedOrm shouldBeSameInstanceAs orm
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `repositories are resolvable by their own interface type`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    val pets: PetRepository by dependencies
                    pets.shouldNotBeNull()
                    // The injected instance is the registry-managed repository.
                    pets shouldBeSameInstanceAs repository<PetRepository>()
                    pets.findAll().isNotEmpty() shouldBe true
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `registerDependencies false leaves the dependency container untouched`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                        registerDependencies = false
                    }
                    // Property delegation would register a startup requirement, so probe the container directly.
                    val ormKey = DependencyKey(TypeInfo(ORMTemplate::class, ORMTemplate::class.starProjectedType))
                    dependencies.contains(ormKey) shouldBe false
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `user-provided dependencies coexist with Storm registrations`() {
        val dataSource = createTestDataSource()
        initializeSchema(dataSource)
        try {
            testApplication {
                application {
                    dependencies {
                        provide<String>("greeting") { "hello" }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    val injectedOrm: ORMTemplate by dependencies
                    injectedOrm.shouldNotBeNull()
                    dependencies.getBlocking<String>(DependencyKey<String>("greeting")) shouldBe "hello"
                }
            }
        } finally {
            dataSource.close()
        }
    }
}
