package st.orm.spring

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import st.orm.PersistenceException
import st.orm.repository.countAll
import st.orm.repository.removeAll
import st.orm.spring.kotlin.springOrmTemplate
import st.orm.spring.model.Visit
import st.orm.template.ORMTemplate
import st.orm.template.transactionBlocking
import java.util.function.Supplier
import javax.sql.DataSource

/**
 * Proves that two application contexts in one JVM get fully isolated transaction integration.
 *
 * Each context carries its own database, its own [DataSourceTransactionManager] and its own template composed via
 * [springOrmTemplate]. Before integration points were instance-scoped, the transaction manager list was held in
 * JVM-global static state that the second context overwrote, so transactions could bind to the wrong manager and
 * standalone templates were silently hijacked by whichever Spring context refreshed last.
 */
internal class DualContextIsolationTest {

    private val contexts = mutableListOf<AnnotationConfigApplicationContext>()

    @AfterEach
    fun closeContexts() {
        contexts.forEach { if (it.isActive) it.close() }
        contexts.clear()
    }

    private fun createDataSource(name: String): DataSource = DataSourceBuilder.create()
        .url("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .driverClassName("org.h2.Driver")
        .build()
        .also { ResourceDatabasePopulator(ClassPathResource("data.sql")).execute(it) }

    private fun createContext(name: String): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        context.registerBean(DataSource::class.java, Supplier { createDataSource(name) })
        context.registerBean(
            DataSourceTransactionManager::class.java,
            Supplier { DataSourceTransactionManager(context.getBean(DataSource::class.java)) },
        )
        context.registerBean(
            ORMTemplate::class.java,
            Supplier {
                springOrmTemplate(context.getBean(DataSource::class.java)) {
                    listOf(context.getBean(DataSourceTransactionManager::class.java))
                }
            },
        )
        context.refresh()
        contexts += context
        return context
    }

    @Test
    fun `transactions of two application contexts bind to their own transaction managers`() {
        val contextA = createContext("dualContextA")
        val contextB = createContext("dualContextB")
        val ormA = contextA.getBean(ORMTemplate::class.java)
        val ormB = contextB.getBean(ORMTemplate::class.java)
        val visitsInA = ormA.countAll<Visit>()
        (visitsInA > 0).shouldBeTrue()
        // A rolled-back delete in context A must not touch context A's data, regardless of context B's presence.
        transactionBlocking {
            ormA.removeAll<Visit>()
            setRollbackOnly()
        }
        ormA.countAll<Visit>() shouldBe visitsInA
        // A committed delete in context B must not touch context A's data.
        transactionBlocking {
            ormB.removeAll<Visit>()
        }
        ormB.countAll<Visit>() shouldBe 0
        ormA.countAll<Visit>() shouldBe visitsInA
    }

    @Test
    fun `closing one application context does not affect the other`() {
        val contextA = createContext("dualCloseA")
        val contextB = createContext("dualCloseB")
        val ormB = contextB.getBean(ORMTemplate::class.java)
        contextA.close()
        transactionBlocking {
            ormB.removeAll<Visit>()
            setRollbackOnly()
        }
        (ormB.countAll<Visit>() > 0).shouldBeTrue()
    }

    @Test
    fun `templates with different transaction providers cannot share one transaction block`() {
        val contextA = createContext("dualMixA")
        val ormA = contextA.getBean(ORMTemplate::class.java)
        val standalone = ORMTemplate.of(createDataSource("dualMixStandalone"))
        val visitsInA = ormA.countAll<Visit>()
        // The block materializes with context A's Spring transaction provider; the standalone template uses the
        // platform-neutral fallback provider, so a single transaction cannot span both.
        assertThrows<PersistenceException> {
            transactionBlocking {
                ormA.removeAll<Visit>()
                standalone.countAll<Visit>()
            }
        }
        // The failed block rolled back context A's work.
        ormA.countAll<Visit>() shouldBe visitsInA
    }

    @Test
    fun `standalone template does not enlist in a spring managed transaction on the same data source`() {
        val contextA = createContext("dualEnlistA")
        val dataSourceA = contextA.getBean(DataSource::class.java)
        val ormA = contextA.getBean(ORMTemplate::class.java)
        val standalone = ORMTemplate.of(dataSourceA)
        val visits = standalone.countAll<Visit>()
        (visits > 0).shouldBeTrue()
        val springTransaction = org.springframework.transaction.support.TransactionTemplate(
            contextA.getBean(DataSourceTransactionManager::class.java),
        )
        springTransaction.execute { status ->
            // The context-owned template joins the Spring-managed transaction through its connection provider.
            ormA.removeAll<Visit>()
            // The standalone template runs on its own connection: the uncommitted delete is not visible, proving it
            // did not silently enlist in the Spring transaction.
            standalone.countAll<Visit>() shouldBe visits
            status.setRollbackOnly()
        }
        standalone.countAll<Visit>() shouldBe visits
    }
}
