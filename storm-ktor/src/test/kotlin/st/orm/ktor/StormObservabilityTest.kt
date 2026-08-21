package st.orm.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
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
internal class StormObservabilityTest {

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
    fun `a convention from the dependency container overrides the naming and key values`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-observed-otel", "/schema.sql")
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                        // The OTel database semantic conventions, mirroring the starters' convention bean.
                        provide<io.micrometer.observation.ObservationConvention<st.orm.micrometer.StormQueryObservationContext>> {
                            st.orm.micrometer.OtelDatabaseObservationConvention("h2database")
                        }
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
                    .hasLowCardinalityKeyValue("db.system.name", "h2database")
                    .hasLowCardinalityKeyValue("db.operation.name", "SELECT")
                    .hasLowCardinalityKeyValue("storm.database", "primary")
            }
        } finally {
            dataSource.close()
        }
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
    fun `transactions are observed through the automatic binding`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-observed-tx", "/schema.sql")
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
                        transactional {
                            get("/pets") {
                                call.respondText(repository<PetRepository>().findAll().size.toString())
                            }
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                TestObservationRegistryAssert.assertThat(observationRegistry)
                    .hasObservationWithNameEqualTo("storm.transaction")
                    .that()
                    .hasLowCardinalityKeyValue("storm.tx.outcome", "committed")
                    .hasLowCardinalityKeyValue("storm.database", "primary")
                    .hasBeenStarted()
                    .hasBeenStopped()
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `a transaction convention from the dependency container overrides the transaction observations`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-observed-tx-convention", "/schema.sql")
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                        provide<io.micrometer.observation.ObservationConvention<st.orm.micrometer.StormTransactionObservationContext>> {
                            object : st.orm.micrometer.StormTransactionObservationConvention() {
                                override fun getName(): String = "db.tx"
                            }
                        }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                    }
                    routing {
                        transactional {
                            get("/pets") {
                                call.respondText(repository<PetRepository>().findAll().size.toString())
                            }
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                TestObservationRegistryAssert.assertThat(observationRegistry)
                    .hasObservationWithNameEqualTo("db.tx")
                    .that()
                    .hasLowCardinalityKeyValue("storm.tx.outcome", "committed")
                    .hasLowCardinalityKeyValue("storm.database", "primary")
                // The query convention is untouched: queries keep their default name.
                TestObservationRegistryAssert.assertThat(observationRegistry)
                    .hasObservationWithNameEqualTo("storm.query")
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
            var registryFromContainer: ObservationRegistry? = null
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                        queryObserver = explicitObserver
                    }
                    registryFromContainer = dependencies.getBlocking(DependencyKey<ObservationRegistry>())
                    routing {
                        get("/pets") {
                            call.respondText(repository<PetRepository>().findAll().size.toString())
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                observedExecutions.get() shouldBeGreaterThan 0
                TestObservationRegistryAssert.assertThat(observationRegistry).doesNotHaveAnyObservation()
                // The registry remains resolvable from the container; the plugin just leaves it unbound.
                registryFromContainer shouldBeSameInstanceAs observationRegistry
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

    @Test
    fun `the configured semantic conventions compose the observer from the database's own data source`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-observed-configured-otel", "/schema.sql")
        try {
            testApplication {
                environment {
                    config = io.ktor.server.config.MapApplicationConfig(
                        "storm.observations.semanticConventions" to "otel",
                    )
                }
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
                    .hasLowCardinalityKeyValue("db.system.name", "h2database")
                    .hasLowCardinalityKeyValue("storm.database", "primary")
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `customize composes on top of the wired integration`() {
        val observationRegistry = TestObservationRegistry.create()
        val dataSource = createTestDataSource("storm-customized", "/schema.sql")
        val observed = AtomicInteger()
        try {
            testApplication {
                application {
                    dependencies {
                        provide<ObservationRegistry> { observationRegistry }
                    }
                    install(Storm) {
                        this.dataSource = dataSource
                        // Runs after the integration is wired, so an override set here wins over the
                        // automatic observation binding.
                        customize = {
                            queryObserver(object : QueryObserver {
                                override fun onExecute(context: st.orm.core.spi.QueryContext): QueryObserver.Observation {
                                    observed.incrementAndGet()
                                    return QueryObserver.Observation.NOOP
                                }
                            })
                        }
                    }
                    routing {
                        get("/pets") {
                            call.respondText(repository<PetRepository>().findAll().size.toString())
                        }
                    }
                }
                client.get("/pets").status shouldBe HttpStatusCode.OK
                observed.get() shouldBeGreaterThan 0
            }
        } finally {
            dataSource.close()
        }
    }
}
