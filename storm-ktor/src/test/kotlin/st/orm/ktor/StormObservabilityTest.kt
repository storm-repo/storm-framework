package st.orm.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import org.junit.jupiter.api.Test
import st.orm.core.spi.QueryObserver
import st.orm.ktor.model.PetRepository
import st.orm.ktor.vet.VetRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the automatic observability binding of the [Storm] plugin: registering an
 * [ObservationRegistry] in the dependency container turns query executions into Micrometer observations,
 * named databases are tagged with `storm.database`, an explicit `queryObserver` wins over the automatic
 * binding, and without a registry queries run unobserved.
 */
class StormObservabilityTest {

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

    @Test
    fun `queries are observed when an ObservationRegistry is registered`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-observed", "/schema.sql")
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    routing {
                        get("/pets") {
                            call.respondText(repository<PetRepository>().findAll().size.toString())
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                TestObservationRegistryAssert.assertThat(observationRegistry)
                    .hasObservationWithNameEqualTo("storm.query")
                    .that()
                    .hasLowCardinalityKeyValue("storm.operation", "SELECT")
                    .hasLowCardinalityKeyValue("storm.data_type", "Pet")
                    // The primary database is tagged too: meters of one name must share a single set of
                    // tag keys, or registries such as Prometheus drop the named databases' series.
                    .hasLowCardinalityKeyValue("storm.database", "primary")
                    .hasBeenStarted()
                    .hasBeenStopped()
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `named database observations carry the database name`() {
        val observationRegistry = TestObservationRegistry.create()
        val clinicDataSource = createTestDataSource("storm-observed-clinic", "/schema.sql")
        val vetsDataSource = createTestDataSource("storm-observed-vets", "/schema-vets.sql")
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                    }
                    install(Storm) {
                        dataSource = clinicDataSource
                        database("vets") {
                            dataSource = vetsDataSource
                            repositories("st.orm.ktor.vet")
                        }
                    }
                    routing {
                        get("/vets") {
                            call.respondText(repository<VetRepository>("vets").findAll().size.toString())
                        }
                    }
                }
                client.get("/vets").status shouldBe HttpStatusCode.OK
                TestObservationRegistryAssert.assertThat(observationRegistry)
                    .hasObservationWithNameEqualTo("storm.query")
                    .that()
                    .hasLowCardinalityKeyValue("storm.database", "vets")
            }
        } finally {
            clinicDataSource.close()
            vetsDataSource.close()
        }
    }

    @Test
    fun `explicit queryObserver wins over the automatic binding`() {
        val observationRegistry = TestObservationRegistry.create()
        val observedExecutions = AtomicInteger()
        val explicitObserver = QueryObserver { _ ->
            observedExecutions.incrementAndGet()
            QueryObserver.Observation.NOOP
        }
        val dataSource = createTestDataSource("storm-explicit-observer", "/schema.sql")
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                        queryObserver = explicitObserver
                    }
                    routing {
                        get("/pets") {
                            call.respondText(repository<PetRepository>().findAll().size.toString())
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                observedExecutions.get() shouldBeGreaterThan 0
                TestObservationRegistryAssert.assertThat(observationRegistry).doesNotHaveAnyObservation()
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `queries run unobserved when no ObservationRegistry is registered`() {
        val dataSource = createTestDataSource("storm-unobserved", "/schema.sql")
        try {
            testApplication {
                application {
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    routing {
                        get("/pets") {
                            call.respondText(repository<PetRepository>().findAll().size.toString())
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
            }
        } finally {
            dataSource.close()
        }
    }
}
