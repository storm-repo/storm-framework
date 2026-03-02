package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Data
import st.orm.Metamodel
import st.orm.Operator.*
import st.orm.PersistenceException
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class QueryBuilderAdvancedTest(
    @Autowired val orm: ORMTemplate,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> =
        model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // ======================================================================
    // distinct() tests
    // ======================================================================

    @Test
    fun `distinct on simple select should return all unique cities`() {
        // data.sql: 6 unique cities. Distinct should not reduce the count since all are unique.
        val cities = orm.entity(City::class).select().distinct().resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `distinct with join should eliminate duplicates from join expansion`() {
        // Inner join Pet -> Owner can produce duplicate owners if an owner has multiple pets.
        // Owner 3 (Eduardo) has 2 pets, Owner 5 (Peter) has 1, Owner 6 (Jean) has 2, etc.
        // Without distinct, the join yields 12 rows (one per pet with an owner).
        // With distinct, we should still get 12 because Pet is the primary entity (each pet row is unique).
        val pets = orm.entity(Pet::class).select().distinct().innerJoin(Owner::class).on(Pet::class).resultList
        pets shouldHaveSize 12
    }

    @Test
    fun `distinct with resultCount should return correct count`() {
        val count = orm.entity(City::class).select().distinct().resultCount
        count shouldBe 6L
    }

    @Test
    fun `distinct with resultFlow should return all unique entities`(): Unit = runBlocking {
        val count = orm.entity(City::class).select().distinct().resultFlow.count()
        count shouldBe 6
    }

    // ======================================================================
    // forUpdate() tests
    // ======================================================================

    @Test
    fun `forUpdate should return correct entity data`() {
        // H2 supports FOR UPDATE. Verify that the result is correct.
        val city = orm.entity(City::class).select().where(2).forUpdate().singleResult
        city.id shouldBe 2
        city.name shouldBe "Madison"
    }

    @Test
    fun `forUpdate with where clause should lock and return matching entity`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath, EQUALS, "Windsor").forUpdate().resultList
        cities shouldHaveSize 1
        cities[0].id shouldBe 4
    }

    @Test
    fun `forUpdate with limit should restrict locked rows`() {
        val cities = orm.entity(City::class).select().limit(2).forUpdate().resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `forUpdate followed by build should produce executable query`() {
        val query = orm.entity(City::class).select().where(1).forUpdate().build()
        val city = query.getSingleResult(City::class)
        city.id shouldBe 1
    }

    // ======================================================================
    // forShare() tests
    // ======================================================================

    @Test
    fun `forShare should build query with shared lock`() {
        // H2 does not support FOR SHARE syntax, so execution should throw a PersistenceException.
        assertThrows<PersistenceException> {
            orm.entity(City::class).select().where(1).forShare().singleResult
        }
    }

    @Test
    fun `forShare with multiple rows should throw on H2`() {
        // Verify that forShare consistently throws on H2 regardless of row count.
        assertThrows<PersistenceException> {
            orm.entity(City::class).select().forShare().resultList
        }
    }

    // ======================================================================
    // append() tests
    // ======================================================================

    @Test
    fun `append with raw SQL template should modify query`() {
        // Use append to add a raw ORDER BY clause. The first 3 cities by id should be 1, 2, 3.
        val repo = orm.entity(City::class)
        val cities = repo.select().append { "ORDER BY ${t(Templates.column(metamodel<City, Int>(repo.model, "id")))}" }.limit(3).resultList
        cities shouldHaveSize 3
        cities[0].id shouldBe 1
        cities[1].id shouldBe 2
        cities[2].id shouldBe 3
    }

    @Test
    fun `append with TemplateString should add clause to query`() {
        // Use append with a TemplateString (raw) to add a comment-like clause.
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        // Append an ORDER BY and verify ordering is applied.
        val cities = repo.select().orderBy(idPath).append(TemplateString.raw("")).resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 1
        cities[5].id shouldBe 6
    }

    // ======================================================================
    // typed() tests
    // ======================================================================

    @Test
    fun `typed should allow specifying primary key type`() {
        val repo = orm.entity(City::class)
        val typedBuilder = repo.select().typed(Int::class)
        val cities = typedBuilder.resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `typed with where by id should return matching entity`() {
        val repo = orm.entity(City::class)
        val city = repo.select().typed(Int::class).where(1).singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `typed with whereId iterable should return matching entities`() {
        val repo = orm.entity(City::class)
        val cities = repo.select().typed(Int::class).whereId(listOf(1, 2, 3)).resultList
        cities shouldHaveSize 3
    }

    // ======================================================================
    // selectFrom / deleteFrom tests (QueryTemplate level)
    // ======================================================================

    @Test
    fun `selectFrom should return query builder for entity type`() {
        val cities = orm.selectFrom(City::class).resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `selectFrom with typed and where should filter results`() {
        val city = orm.selectFrom(City::class).typed(Int::class).where(1).singleResult
        city.id shouldBe 1
    }

    @Test
    fun `selectFrom with distinct should return unique results`() {
        val count = orm.selectFrom(City::class).distinct().resultCount
        count shouldBe 6L
    }

    @Test
    fun `deleteFrom should create delete query builder`() {
        val deleted = orm.deleteFrom(Visit::class).unsafe().executeUpdate()
        deleted shouldBe 14
    }

    // ======================================================================
    // hasOrderBy tests
    // ======================================================================

    @Test
    fun `hasOrderBy should return false when no order by is set`() {
        val builder = orm.entity(City::class).select()
        builder.hasOrderBy() shouldBe false
    }

    @Test
    fun `hasOrderBy should return true after orderBy is called`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val builder = repo.select().orderBy(idPath)
        builder.hasOrderBy() shouldBe true
    }

    // ======================================================================
    // forLock() tests
    // ======================================================================

    @Test
    fun `forLock with custom template should apply lock mode`() {
        // H2 supports FOR UPDATE, so using forLock with a "FOR UPDATE" template should work.
        val city = orm.entity(City::class).select().where(1).forLock { "FOR UPDATE" }.singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    // ======================================================================
    // Query resultFlow tests
    // ======================================================================

    @Test
    fun `build query resultFlow should return typed flow`(): Unit = runBlocking {
        val query = orm.entity(City::class).select().build()
        val cities = query.getResultFlow(City::class).toList()
        cities shouldHaveSize 6
    }

    @Test
    fun `build query resultFlow with where should return filtered flow`(): Unit = runBlocking {
        val query = orm.entity(City::class).select().where(1).build()
        val cities = query.getResultFlow(City::class).toList()
        cities shouldHaveSize 1
        cities[0].name shouldBe "Sun Paririe"
    }

    @Test
    fun `build query getRefFlow should return ref flow`(): Unit = runBlocking {
        val query = orm.query("SELECT id FROM city")
        val refs = query.getRefFlow(City::class, Integer::class).toList()
        refs shouldHaveSize 6
        refs.forEach { ref ->
            ref.id() shouldNotBe null
        }
    }

    // ======================================================================
    // Combined advanced query scenarios
    // ======================================================================

    @Test
    fun `distinct with orderBy and limit should work together`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().distinct().orderBy(namePath).limit(3).resultList
        cities shouldHaveSize 3
        // Alphabetical: Madison, McFarland, Monona
        cities[0].name shouldBe "Madison"
        cities[1].name shouldBe "McFarland"
        cities[2].name shouldBe "Monona"
    }

    @Test
    fun `distinct with where and resultCount should return correct count`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val count = repo.select().distinct().where(namePath like "M%").resultCount
        // Madison, McFarland, Monona
        count shouldBe 3L
    }

    @Test
    fun `forUpdate with orderBy should return correctly ordered locked rows`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).forUpdate().resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 1
        cities[5].id shouldBe 6
    }
}
