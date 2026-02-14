package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.repository.EntityRepository
import st.orm.repository.ProjectionRepository
import st.orm.template.model.City
import st.orm.template.model.OwnerView

/**
 * Custom EntityRepository with Kotlin default methods.
 *
 * When a proxy is created for this interface, the default methods are compiled into a
 * `CityCustomRepo$DefaultImpls` class. The proxy invocation handler in ORMTemplateImpl detects
 * these as Kotlin default methods via `ORMReflectionImpl.isDefaultMethod()` and delegates to
 * `ORMReflectionImpl.execute()`, which in turn calls `findKotlinDefault()` and `scanMethods()`
 * to locate and invoke the static helper in `DefaultImpls`.
 */
interface CityCustomRepo : EntityRepository<City, Int> {

    /**
     * Default method that delegates to [count].
     */
    fun cityCount(): Long = count()

    /**
     * Default method that delegates to [findAll] and maps the result.
     */
    fun allCityNames(): List<String> = findAll().map { it.name }

    /**
     * Default method that delegates to [findById].
     */
    fun findCityById(id: Int): City? = findById(id)

    /**
     * Default method that delegates to [exists].
     */
    fun hasCities(): Boolean = exists()
}

/**
 * Custom ProjectionRepository with a Kotlin default method.
 */
interface OwnerViewCustomRepo : ProjectionRepository<OwnerView, Int> {

    /**
     * Default method that delegates to [count].
     */
    fun ownerViewCount(): Long = count()

    /**
     * Default method that delegates to [findAll] and maps the result.
     */
    fun allFirstNames(): List<String> = findAll().map { it.firstName }
}

/**
 * Tests for custom repository interfaces with Kotlin default methods.
 *
 * These tests exercise the proxy dispatch path in ORMTemplateImpl that handles Kotlin default
 * methods, specifically:
 * - `ORMReflectionImpl.isDefaultMethod()` which checks for Kotlin @Metadata annotation
 * - `ORMReflectionImpl.execute()` which delegates to `findKotlinDefault()`
 * - `ORMReflectionImpl.findKotlinDefault()` which locates the `$DefaultImpls` class
 * - `ORMReflectionImpl.scanMethods()` which scans for the matching static method
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class CustomRepositoryTest(
    @Autowired val orm: ORMTemplate,
) {

    // --- Custom EntityRepository with default methods ---

    @Test
    fun `custom repository default method calling findAll should work`() {
        // data.sql inserts 6 cities in order: Sun Paririe, Madison, McFarland, Windsor, Monona, Waunakee.
        val repo = orm.repository(CityCustomRepo::class)
        val names = repo.allCityNames()
        names shouldHaveSize 6
        names shouldBe listOf("Sun Paririe", "Madison", "McFarland", "Windsor", "Monona", "Waunakee")
    }

    @Test
    fun `custom repository default method calling count should work`() {
        // data.sql inserts 6 cities (ids 1-6).
        val repo = orm.repository(CityCustomRepo::class)
        repo.cityCount() shouldBe 6
    }

    @Test
    fun `custom repository default method calling find should work`() {
        // data.sql: City(id=1, name='Sun Paririe').
        val repo = orm.repository(CityCustomRepo::class)
        val city = repo.findCityById(1)
        city.shouldNotBeNull()
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `custom repository default method calling find should return null when not found`() {
        val repo = orm.repository(CityCustomRepo::class)
        val city = repo.findCityById(999)
        city.shouldBeNull()
    }

    @Test
    fun `custom repository default method calling exists should work`() {
        val repo = orm.repository(CityCustomRepo::class)
        repo.hasCities() shouldBe true
    }

    @Test
    fun `custom repository standard methods should still work through custom interface`() {
        // Standard EntityRepository methods should still be dispatched correctly through the proxy.
        // data.sql inserts 6 cities; City(id=2, name='Madison'), City(id=3, name='McFarland').
        val repo = orm.repository(CityCustomRepo::class)
        repo.count() shouldBe 6
        repo.findAll() shouldHaveSize 6
        repo.findById(2).shouldNotBeNull().name shouldBe "Madison"
        repo.existsById(3) shouldBe true
        repo.existsById(999) shouldBe false
    }

    // --- Custom ProjectionRepository with default methods ---

    @Test
    fun `custom projection repository default method should work`() {
        // data.sql inserts 10 owners; OwnerView is backed by owner_view.
        val repo = orm.repository(OwnerViewCustomRepo::class)
        repo.ownerViewCount() shouldBe 10
    }

    @Test
    fun `custom projection repository default method returning mapped list should work`() {
        // data.sql inserts owners in order: Betty, George, Eduardo, Harold, Peter, Jean, Jeff, Maria, David, Carlos.
        val repo = orm.repository(OwnerViewCustomRepo::class)
        val names = repo.allFirstNames()
        names shouldHaveSize 10
        names[0] shouldBe "Betty"
        names[1] shouldBe "George"
    }

    @Test
    fun `custom projection repository standard methods should still work`() {
        // data.sql: 10 owners total, Owner(id=1, first_name='Betty').
        val repo = orm.repository(OwnerViewCustomRepo::class)
        repo.count() shouldBe 10
        repo.findAll() shouldHaveSize 10
        repo.findById(1).shouldNotBeNull().firstName shouldBe "Betty"
    }

    // --- Proxy behavior tests ---

    @Test
    fun `custom repository proxy toString should return meaningful string`() {
        val repo = orm.repository(CityCustomRepo::class)
        repo.toString() shouldBe "RepositoryProxy(CityCustomRepo)"
    }

    @Test
    fun `custom repository proxy hashCode should not throw`() {
        val repo = orm.repository(CityCustomRepo::class)
        val hash = repo.hashCode()
        hash shouldNotBe null
    }

    @Test
    fun `custom repository proxy equals should use identity`() {
        val repo = orm.repository(CityCustomRepo::class)
        (repo == repo) shouldBe true
    }

    @Test
    fun `custom repository proxy equals should return false for different proxy`() {
        val repo1 = orm.repository(CityCustomRepo::class)
        val repo2 = orm.repository(CityCustomRepo::class)
        (repo1 === repo2) shouldBe false
    }

    @Test
    fun `custom repository proxy orm property should return ORMTemplate`() {
        val repo = orm.repository(CityCustomRepo::class)
        repo.orm.shouldNotBeNull()
        repo.orm.shouldBeInstanceOf<ORMTemplate>()
    }
}
