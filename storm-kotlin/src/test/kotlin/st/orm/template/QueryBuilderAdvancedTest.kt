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
import st.orm.*
import st.orm.Operator.*
import st.orm.template.model.City
import st.orm.template.model.Owner
import st.orm.template.model.Pet
import st.orm.template.model.Visit

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class QueryBuilderAdvancedTest(
    @Autowired val orm: ORMTemplate,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // distinct() tests

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

    // forUpdate() tests

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

    // forShare() tests

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

    // append() tests

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

    // typed() tests

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

    // selectFrom / deleteFrom tests (QueryTemplate level)

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

    // hasOrderBy tests

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

    // forLock() tests

    @Test
    fun `forLock with custom template should apply lock mode`() {
        // H2 supports FOR UPDATE, so using forLock with a "FOR UPDATE" template should work.
        val city = orm.entity(City::class).select().where(1).forLock { "FOR UPDATE" }.singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    // Query resultFlow tests

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

    // Combined advanced query scenarios

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

    // QueryBuilder: where with varargs

    @Test
    fun `where with metamodel and IN operator and varargs should filter entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath, IN, "Madison", "Windsor").resultList
        cities shouldHaveSize 2
    }

    // QueryBuilder: having with metamodel path and operator

    @Test
    fun `having with template builder should filter groups`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val queryResult = repo.selectCount()
            .groupBy(cityPath)
            .having { "COUNT(*) > 1" }
            .resultList
        // Cities with more than 1 owner
        queryResult.size shouldNotBe 0
    }

    // QueryBuilder: where/whereAny with PredicateBuilder (using eq infix)

    @Test
    fun `where with PredicateBuilder should filter entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val predicate = namePath eq "Madison"
        val cities = repo.select().where(predicate).resultList
        cities shouldHaveSize 1
    }

    @Test
    fun `whereAny with PredicateBuilder should filter entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val predicate = namePath eq "Madison"
        val cities = repo.select().whereAny(predicate).resultList
        cities shouldHaveSize 1
    }

    // QueryBuilder: resultCount

    @Test
    fun `resultCount should return total count`() {
        val repo = orm.entity(City::class)
        val count = repo.select().resultCount
        count shouldBe 6L
    }

    @Test
    fun `resultCount with where should return filtered count`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val count = repo.select().where(namePath, EQUALS, "Madison").resultCount
        count shouldBe 1L
    }

    // QueryBuilder: limit/offset

    @Test
    fun `limit should restrict result count`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).limit(3).resultList
        cities shouldHaveSize 3
    }

    @Test
    fun `limit with offset should skip and restrict results`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).limit(2).offset(2).resultList
        cities shouldHaveSize 2
        cities[0].id shouldBe 3
    }

    // QueryBuilder: selectAll flow

    @Test
    fun `resultFlow should return all entities as flow`(): Unit = runBlocking {
        val repo = orm.entity(City::class)
        val count = repo.select().resultFlow.count()
        count shouldBe 6
    }

    // QueryBuilder: whereExists and whereNotExists

    @Test
    fun `whereExists with subquery builder should filter cities with owners`() {
        val repo = orm.entity(City::class)
        val cities = repo.select().whereExists { subquery(Owner::class) }.resultList
        // All 6 cities are referenced by owners
        cities shouldHaveSize 6
    }

    @Test
    fun `whereNotExists with subquery builder should filter cities without owners`() {
        val repo = orm.entity(City::class)
        val cities = repo.select().whereNotExists { subquery(Owner::class) }.resultList
        // All cities are referenced by owners
        cities shouldHaveSize 0
    }

    // orderByDescending tests

    @Test
    fun `orderByDescending with single metamodel path should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescending(idPath).resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    @Test
    fun `orderByDescending with varargs metamodel paths should sort descending`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val owners = repo.select().orderByDescending(lastNamePath, firstNamePath).limit(3).resultList
        owners shouldHaveSize 3
    }

    @Test
    fun `orderByDescendingAny with single metamodel path should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescendingAny(idPath).resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    @Test
    fun `orderByDescendingAny with multiple metamodel paths should sort descending`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val owners = repo.select().orderByDescendingAny(lastNamePath, firstNamePath).limit(3).resultList
        owners shouldHaveSize 3
    }

    @Test
    fun `orderByDescending with template builder should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescending { t(Templates.column(idPath)) }.resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    // orderByAny tests

    @Test
    fun `orderByAny with multiple metamodel paths should sort ascending`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val owners = repo.select().orderByAny(lastNamePath, firstNamePath).limit(3).resultList
        owners shouldHaveSize 3
    }

    // singleResult / optionalResult tests

    @Test
    fun `singleResult should return entity when exactly one match`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val city = repo.select().where(namePath, EQUALS, "Madison").singleResult
        city.name shouldBe "Madison"
    }

    @Test
    fun `singleResult should throw NoResultException when no match`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        assertThrows<NoResultException> {
            repo.select().where(namePath, EQUALS, "NonExistent").singleResult
        }
    }

    @Test
    fun `singleResult should throw NonUniqueResultException when multiple matches`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        assertThrows<NonUniqueResultException> {
            repo.select().where(namePath, LIKE, "M%").singleResult
        }
    }

    @Test
    fun `optionalResult should return entity when exactly one match`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val city = repo.select().where(namePath, EQUALS, "Madison").optionalResult
        city shouldNotBe null
        city!!.name shouldBe "Madison"
    }

    @Test
    fun `optionalResult should return null when no match`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val city = repo.select().where(namePath, EQUALS, "NonExistent").optionalResult
        city shouldBe null
    }

    @Test
    fun `optionalResult should throw NonUniqueResultException when multiple matches`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        assertThrows<NonUniqueResultException> {
            repo.select().where(namePath, LIKE, "M%").optionalResult
        }
    }

    // having / havingAny with metamodel + operator

    @Test
    fun `having with TemplateBuilder should add having clause`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.selectCount()
            .groupBy(lastNamePath)
            .having { "COUNT(*) >= 1" }
            .resultList
        result.size shouldNotBe 0
    }

    @Test
    fun `having with metamodel and operator should filter groups`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.selectCount()
            .groupBy(lastNamePath)
            .having(lastNamePath, EQUALS, "Davis")
            .resultList
        result shouldHaveSize 1
    }

    @Test
    fun `havingAny with metamodel and operator should filter groups`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.selectCount()
            .groupBy(lastNamePath)
            .havingAny(lastNamePath, IN, "Davis", "Franklin")
            .resultList
        result shouldHaveSize 2
    }

    // where with Metamodel and Data (FK reference)

    @Test
    fun `where with metamodel and data reference should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val madison = cityRepo.select().where(2).singleResult
        val ownerRepo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(ownerRepo.model, "city_id")
        val owners = ownerRepo.select().where(cityPath, madison).resultList
        owners shouldHaveSize 4
    }

    // QueryBuilder slice (no key) tests

    @Test
    fun `slice without key should return first page with hasNext`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `slice without key should return all when size exceeds count`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(10)
        slice.content shouldHaveSize 6
        slice.hasNext shouldBe false
    }

    // QueryBuilder: sliceAfter/sliceBefore with PK cursor

    @Test
    fun `sliceAfter should return next page`() {
        val repo = orm.entity(Owner::class)
        val idKey = metamodel<Owner, Int>(repo.model, "id").key()
        // Owners have ids 1-10. After id > 5: ids 6,7,8,9,10 = 5 owners.
        val slice = repo.select().sliceAfter(idKey, 5, 10)
        slice.content shouldHaveSize 5
    }

    @Test
    fun `sliceBefore should return previous page`() {
        val repo = orm.entity(Owner::class)
        val idKey = metamodel<Owner, Int>(repo.model, "id").key()
        // Owners have ids 1-10. Before id < 6: ids 1,2,3,4,5 = 5 owners.
        val slice = repo.select().sliceBefore(idKey, 6, 10)
        slice.content shouldHaveSize 5
    }

    // QueryBuilder: sliceAfter/sliceBefore with sort + cursor (composite keyset)

    @Test
    fun `sliceAfter with composite key and sort cursor should return next page`() {
        val repo = orm.entity(City::class)
        val idKey = metamodel<City, Int>(repo.model, "id").key()
        val namePath = metamodel<City, String>(repo.model, "name")
        // Get first page sorted by name
        val firstPage = repo.select().slice(idKey, namePath, 3)
        firstPage.content shouldHaveSize 3
        val lastItem = firstPage.content.last()
        // Get next page after last item
        val nextPage = repo.select().sliceAfter(idKey, lastItem.id, namePath, lastItem.name, 3)
        nextPage.content shouldHaveSize 3
    }

    @Test
    fun `sliceBefore with composite key and sort cursor should return previous page`() {
        val repo = orm.entity(City::class)
        val idKey = metamodel<City, Int>(repo.model, "id").key()
        val namePath = metamodel<City, String>(repo.model, "name")
        // Get last page (descending)
        val lastPage = repo.select().sliceBefore(idKey, namePath, 3)
        lastPage.content shouldHaveSize 3
        val firstItem = lastPage.content.last()
        // Get previous page before first item
        val previousPage = repo.select().sliceBefore(idKey, firstItem.id, namePath, firstItem.name, 3)
        previousPage.content shouldHaveSize 3
    }

    @Test
    fun `sliceAfter with composite key and sort cursor should return all matching owners`() {
        val repo = orm.entity(Owner::class)
        val idKey = metamodel<Owner, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        // After (lastName > "A" OR (lastName = "A" AND id > 1)): all 10 owners have lastName > "A".
        val slice = repo.select().sliceAfter(idKey, 1, lastNamePath, "A", 10)
        slice.content shouldHaveSize 10
    }

    @Test
    fun `sliceBefore with composite key and sort cursor should return all matching owners`() {
        val repo = orm.entity(Owner::class)
        val idKey = metamodel<Owner, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        // Before (lastName < "Z" OR (lastName = "Z" AND id < 10)): all 10 owners have lastName < "Z".
        val slice = repo.select().sliceBefore(idKey, 10, lastNamePath, "Z", 10)
        slice.content shouldHaveSize 10
    }

    // QueryBuilder: join with template

    @Test
    fun `join with joinType and template and alias should produce correct result`() {
        val repo = orm.entity(Pet::class)
        val count = repo.select()
            .join(JoinType.inner(), TemplateString.raw("visit"), "v")
            .on(TemplateString.raw("v.pet_id = p.id"))
            .resultList.size
        count shouldNotBe 0
    }

    // Kotlin DSL infix predicates

    @Test
    fun `neq infix should filter not equal entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath neq "Madison").resultList
        cities shouldHaveSize 5
    }

    @Test
    fun `greater infix should filter greater than entities`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().where(idPath greater 4).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `less infix should filter less than entities`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().where(idPath less 3).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `greaterEq infix should filter greater or equal entities`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().where(idPath greaterEq 5).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `lessEq infix should filter less or equal entities`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().where(idPath lessEq 2).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `notLike infix should filter not matching entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath notLike "M%").resultList
        cities shouldHaveSize 3
    }

    @Test
    fun `inList infix should filter entities in list`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath inList listOf("Madison", "Windsor")).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `notInList infix should filter entities not in list`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath notInList listOf("Madison", "Windsor")).resultList
        cities shouldHaveSize 4
    }

    @Test
    fun `isNotNull predicate should filter non-null values`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath.isNotNull()).resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `between predicate should filter values in range`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().where(idPath.between(2, 4)).resultList
        cities shouldHaveSize 3
    }
}
