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
import st.orm.Metamodel
import st.orm.NoResultException
import st.orm.NonUniqueResultException
import st.orm.Operator.*
import st.orm.Ref
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class QueryBuilderTest(
    @Autowired val orm: ORMTemplate
) {

    // Helper to get a typed metamodel from column for use in typed where/orderBy calls.
    @Suppress("UNCHECKED_CAST")
    private fun <T : st.orm.Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> {
        return model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>
    }

    // --- WHERE clause tests ---

    @Test
    fun `select with where by id should return matching entity`() {
        // data.sql: City(id=1, name='Sun Paririe').
        val city = orm.entity(City::class).select().where(1).singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `select with where by record should return matching entity`() {
        // data.sql: City(id=2, name='Madison'). Filtering by record matches on PK.
        val city = City(id = 2, name = "Madison")
        val result = orm.entity(City::class).select().where(city).singleResult
        result.id shouldBe 2
        result.name shouldBe "Madison"
    }

    @Test
    fun `select with whereId iterable should return matching entities`() {
        val cities = orm.entity(City::class).select().whereId(listOf(1, 2, 3)).resultList
        cities shouldHaveSize 3
    }

    @Test
    fun `select with where metamodel path and EQUALS operator should return matching entity`() {
        // data.sql: Only one city has name 'Madison' (id=2).
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath, EQUALS, "Madison").resultList
        cities shouldHaveSize 1
        cities[0].name shouldBe "Madison"
    }

    @Test
    fun `select with where metamodel path and IN operator should return matching entities`() {
        // data.sql: 'Madison' (id=2) and 'Windsor' (id=4) are both present.
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where(namePath, IN, listOf("Madison", "Windsor")).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `select with whereBuilder should return matching entity`() {
        // data.sql: City(id=3, name='McFarland').
        val repo = orm.entity(City::class)
        val result = repo.select().whereBuilder { whereId(3) }.singleResult
        result.id shouldBe 3
        result.name shouldBe "McFarland"
    }

    @Test
    fun `select with whereExists subquery should return owners with pets`() {
        val ownerRepo = orm.entity(Owner::class)
        val results = ownerRepo.select().whereExists { subquery(Pet::class) }.resultList
        // All 10 owners have at least one pet.
        results shouldHaveSize 10
    }

    @Test
    fun `select with whereNotExists subquery should return owners without pets`() {
        val cityRepo = orm.entity(City::class)
        // All cities 1-6 are referenced by owners, so this checks cities with no owner.
        val results = cityRepo.select().whereNotExists { subquery(Owner::class) }.resultList
        results shouldHaveSize 0
    }

    // --- Result method tests ---

    @Test
    fun `resultList should return all matching entities`() {
        // data.sql inserts 6 cities (ids 1-6).
        val cities = orm.entity(City::class).select().resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `resultCount should return count of all entities`() {
        // data.sql inserts 6 cities.
        val count = orm.entity(City::class).select().resultCount
        count shouldBe 6L
    }

    @Test
    fun `selectCount should return count of all entities`() {
        // data.sql inserts 6 cities.
        val count = orm.entity(City::class).selectCount().singleResult
        count shouldBe 6L
    }

    @Test
    fun `singleResult should return entity when exactly one match`() {
        // data.sql: City(id=1, name='Sun Paririe').
        val city = orm.entity(City::class).select().where(1).singleResult
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `singleResult should throw NonUniqueResultException when multiple matches`() {
        assertThrows<NonUniqueResultException> {
            orm.entity(City::class).select().singleResult
        }
    }

    @Test
    fun `singleResult should throw NoResultException when no matches`() {
        assertThrows<NoResultException> {
            orm.entity(City::class).select().where(999).singleResult
        }
    }

    @Test
    fun `optionalResult should return entity when exactly one match`() {
        val city = orm.entity(City::class).select().where(1).optionalResult
        city shouldNotBe null
        city!!.name shouldBe "Sun Paririe"
    }

    @Test
    fun `optionalResult should return null when no matches`() {
        val city = orm.entity(City::class).select().where(999).optionalResult
        city shouldBe null
    }

    @Test
    fun `optionalResult should throw NonUniqueResultException when multiple matches`() {
        assertThrows<NonUniqueResultException> {
            orm.entity(City::class).select().optionalResult
        }
    }

    @Test
    fun `resultFlow should return all entities as flow`(): Unit = runBlocking {
        val count = orm.entity(City::class).select().resultFlow.count()
        count shouldBe 6
    }

    @Test
    fun `resultFlow with where should return matching entities`(): Unit = runBlocking {
        val cities = orm.entity(City::class).select().where(1).resultFlow.toList()
        cities shouldHaveSize 1
        cities[0].name shouldBe "Sun Paririe"
    }

    // --- Distinct tests ---

    @Test
    fun `distinct should return unique results`() {
        val cities = orm.entity(City::class).select().distinct().resultList
        cities shouldHaveSize 6
    }

    // --- OrderBy tests ---

    @Test
    fun `orderBy should sort results ascending`() {
        // Alphabetical order of 6 cities: Madison, McFarland, Monona, Sun Paririe, Waunakee, Windsor.
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().orderBy(namePath).resultList
        cities[0].name shouldBe "Madison"
        cities[1].name shouldBe "McFarland"
        cities[2].name shouldBe "Monona"
    }

    @Test
    fun `orderByDescending should sort results descending`() {
        // Reverse alphabetical: Windsor, Waunakee, Sun Paririe, Monona, McFarland, Madison.
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().orderByDescending(namePath).resultList
        cities[0].name shouldBe "Windsor"
        cities[1].name shouldBe "Waunakee"
        cities[2].name shouldBe "Sun Paririe"
    }

    // --- Limit and Offset tests ---

    @Test
    fun `limit should restrict number of results`() {
        val cities = orm.entity(City::class).select().limit(3).resultList
        cities shouldHaveSize 3
    }

    @Test
    fun `offset should skip results`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).offset(2).limit(2).resultList
        cities shouldHaveSize 2
        cities[0].id shouldBe 3
        cities[1].id shouldBe 4
    }

    @Test
    fun `limit and offset combined for pagination`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        // Page 1
        val page1 = repo.select().orderBy(idPath).limit(2).offset(0).resultList
        page1 shouldHaveSize 2
        page1[0].id shouldBe 1
        page1[1].id shouldBe 2
        // Page 2
        val page2 = repo.select().orderBy(idPath).limit(2).offset(2).resultList
        page2 shouldHaveSize 2
        page2[0].id shouldBe 3
        page2[1].id shouldBe 4
    }

    // --- GroupBy and Having tests ---

    @Test
    fun `selectCount with groupBy should group results`() {
        // data.sql: 10 owners across 6 distinct cities. Grouping by city_id yields 6 groups.
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val counts = repo.selectCount().groupBy(cityPath).resultList
        counts shouldHaveSize 6
    }

    // --- ForUpdate tests ---

    @Test
    fun `forUpdate should not change result content`() {
        val city = orm.entity(City::class).select().where(1).forUpdate().singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    // --- Safe mode tests ---

    @Test
    fun `safe delete without where should succeed`() {
        // data.sql inserts 14 visits. safe() allows delete without a where clause.
        val repo = orm.entity(Visit::class)
        val count = repo.delete().safe().executeUpdate()
        count shouldBe 14
    }

    // --- Join tests ---

    @Test
    fun `innerJoin should return entities with matching join`() {
        // data.sql: 13 pets total, but Pet(id=13, name='Sly') has NULL owner_id.
        // Inner join excludes pets with no matching owner, yielding 12.
        val repo = orm.entity(Pet::class)
        val pets = repo.select().innerJoin(Owner::class).on(Pet::class).resultList
        pets shouldHaveSize 12
    }

    @Test
    fun `leftJoin should include entities without matching join`() {
        // data.sql: 13 pets total. Left join preserves all pets, including Pet 13 with NULL owner.
        val repo = orm.entity(Pet::class)
        val pets = repo.select().leftJoin(Owner::class).on(Pet::class).resultList
        pets shouldHaveSize 13
    }

    // --- Delete tests ---

    @Test
    fun `delete with where by id should remove matching entity`() {
        // data.sql: Vet(id=1, 'James', 'Carter') has no vet_specialty entries, safe to delete.
        // After deleting 1 of 6 vets, 5 remain.
        val repo = orm.entity(Vet::class)
        val deleted = repo.delete().where(1).executeUpdate()
        deleted shouldBe 1
        repo.count() shouldBe 5
    }

    @Test
    fun `delete with where by record should remove matching entity`() {
        // data.sql: Vet(id=6, 'Sharon', 'Jenkins') has no vet_specialty entries, safe to delete.
        // After deleting 1 of 6 vets, 5 remain.
        val repo = orm.entity(Vet::class)
        val vet = repo.select().where(6).singleResult
        val deleted = repo.delete().where(vet).executeUpdate()
        deleted shouldBe 1
        repo.count() shouldBe 5
    }

    // --- WhereBuilder predicate chaining tests ---

    @Test
    fun `whereBuilder with and should combine predicates`() {
        // data.sql: Owner(id=4, first_name='Harold', last_name='Davis') is the only match.
        val repo = orm.entity(Owner::class)
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.select().whereBuilder {
            where(firstNamePath, EQUALS, "Harold") and where(lastNamePath, EQUALS, "Davis")
        }.resultList
        result shouldHaveSize 1
        result[0].firstName shouldBe "Harold"
        result[0].lastName shouldBe "Davis"
    }

    @Test
    fun `whereBuilder with or should match either predicate`() {
        // data.sql: City(id=2, name='Madison') and City(id=4, name='Windsor').
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") or where(namePath, EQUALS, "Windsor")
        }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `whereBuilder with TRUE should match all records`() {
        val repo = orm.entity(City::class)
        val result = repo.select().whereBuilder { TRUE() }.resultList
        result shouldHaveSize 6
    }

    @Test
    fun `whereBuilder with FALSE should match no records`() {
        val repo = orm.entity(City::class)
        val result = repo.select().whereBuilder { FALSE() }.resultList
        result shouldHaveSize 0
    }

    @Test
    fun `whereBuilder with complex and-or predicate`() {
        // data.sql: Owner(id=1, 'Betty', 'Davis') and Owner(id=2, 'George', 'Franklin').
        val repo = orm.entity(Owner::class)
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.select().whereBuilder {
            (where(firstNamePath, EQUALS, "Betty") and where(lastNamePath, EQUALS, "Davis")) or
                (where(firstNamePath, EQUALS, "George") and where(lastNamePath, EQUALS, "Franklin"))
        }.resultList
        result shouldHaveSize 2
    }

    // --- ColumnImpl tests ---

    @Test
    fun `city model columns should have correct properties`() {
        val model = orm.entity(City::class).model
        val columns = model.columns
        // City has: id (PK), name
        columns.size shouldBe 2

        val idCol = columns.first { it.name == "id" }
        idCol.primaryKey shouldBe true
        idCol.nullable shouldBe false
        idCol.foreignKey shouldBe false
        idCol.version shouldBe false
        idCol.ref shouldBe false
        idCol.insertable shouldBe true

        val nameCol = columns.first { it.name == "name" }
        nameCol.primaryKey shouldBe false
        nameCol.nullable shouldBe false
        nameCol.foreignKey shouldBe false
        nameCol.insertable shouldBe true
        nameCol.updatable shouldBe true
    }

    @Test
    fun `owner model columns should detect version column`() {
        val model = orm.entity(Owner::class).model
        val columns = model.columns

        val versionCol = columns.first { it.name == "version" }
        versionCol.version shouldBe true
        versionCol.primaryKey shouldBe false
    }

    @Test
    fun `owner model columns should detect foreign key`() {
        val model = orm.entity(Owner::class).model
        val columns = model.columns

        val cityCol = columns.first { it.name == "city_id" }
        cityCol.foreignKey shouldBe true
        cityCol.nullable shouldBe true
    }

    @Test
    fun `pet model columns should detect non-updatable column`() {
        val model = orm.entity(Pet::class).model
        val columns = model.columns

        val birthDateCol = columns.first { it.name == "birth_date" }
        birthDateCol.updatable shouldBe false
        birthDateCol.insertable shouldBe true

        val typeCol = columns.first { it.name == "type_id" }
        typeCol.foreignKey shouldBe true
        typeCol.updatable shouldBe false
    }

    @Test
    fun `pet model columns should detect nullable foreign key`() {
        val model = orm.entity(Pet::class).model
        val columns = model.columns

        val ownerCol = columns.first { it.name == "owner_id" }
        ownerCol.foreignKey shouldBe true
        ownerCol.nullable shouldBe true
    }

    @Test
    fun `model should report correct table name`() {
        val model = orm.entity(City::class).model
        model.name shouldBe "city"
    }

    @Test
    fun `model should report correct primary key type`() {
        val model = orm.entity(City::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `model should report correct entity type`() {
        val model = orm.entity(City::class).model
        model.type shouldBe City::class
    }

    @Test
    fun `column metamodel should not be null`() {
        val model = orm.entity(City::class).model
        model.columns.forEach { col ->
            col.metamodel shouldNotBe null
        }
    }

    // --- Infix DSL operator tests ---

    @Test
    fun `eq infix operator should filter by equality`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath eq "Madison").resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Madison"
    }

    @Test
    fun `neq infix operator should filter by inequality`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath neq "Madison").resultList
        result shouldHaveSize 5
    }

    @Test
    fun `like infix operator should match pattern`() {
        // data.sql: Cities starting with 'M': Madison, McFarland, Monona (3 of 6).
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath like "M%").resultList
        result shouldHaveSize 3
    }

    @Test
    fun `notLike infix operator should exclude pattern`() {
        // data.sql: Cities NOT starting with 'M': Sun Paririe, Windsor, Waunakee (3 of 6).
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath notLike "M%").resultList
        result shouldHaveSize 3
    }

    @Test
    fun `greater infix operator should filter by greater than`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val result = repo.select().where(idPath greater 4).resultList
        // IDs 5, 6
        result shouldHaveSize 2
    }

    @Test
    fun `less infix operator should filter by less than`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val result = repo.select().where(idPath less 3).resultList
        // IDs 1, 2
        result shouldHaveSize 2
    }

    @Test
    fun `greaterEq infix operator should filter by greater than or equal`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val result = repo.select().where(idPath greaterEq 5).resultList
        // IDs 5, 6
        result shouldHaveSize 2
    }

    @Test
    fun `lessEq infix operator should filter by less than or equal`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val result = repo.select().where(idPath lessEq 2).resultList
        // IDs 1, 2
        result shouldHaveSize 2
    }

    @Test
    fun `inList infix operator should match values in list`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath inList listOf("Madison", "Windsor", "Monona")).resultList
        result shouldHaveSize 3
    }

    @Test
    fun `notInList infix operator should exclude values in list`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath notInList listOf("Madison", "Windsor", "Monona")).resultList
        // Sun Paririe, McFarland, Waunakee
        result shouldHaveSize 3
    }

    @Test
    fun `isNull should find entities with null values`() {
        // data.sql: Pet(id=13, name='Sly', owner_id=NULL) is the only pet with a null owner.
        val repo = orm.entity(Pet::class)
        val ownerPath = metamodel<Pet, Any>(repo.model, "owner_id")
        val result = repo.select().where(ownerPath.isNull()).resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Sly"
    }

    @Test
    fun `isNotNull should find entities with non-null values`() {
        // data.sql: 13 pets total, 1 with NULL owner (Sly), so 12 have non-null owners.
        val repo = orm.entity(Pet::class)
        val ownerPath = metamodel<Pet, Any>(repo.model, "owner_id")
        val result = repo.select().where(ownerPath.isNotNull()).resultList
        result shouldHaveSize 12
    }

    @Test
    fun `between should filter by range`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val result = repo.select().where(idPath.between(2, 4)).resultList
        // IDs 2, 3, 4
        result shouldHaveSize 3
    }

    // --- Combined infix and predicate builder tests ---

    @Test
    fun `infix operators with and combinator`() {
        val repo = orm.entity(Owner::class)
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val result = repo.select().where(
            (firstNamePath eq "Harold") and (lastNamePath eq "Davis")
        ).resultList
        result shouldHaveSize 1
        result[0].firstName shouldBe "Harold"
    }

    @Test
    fun `infix operators with or combinator`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(
            (namePath eq "Madison") or (namePath eq "Windsor")
        ).resultList
        result shouldHaveSize 2
    }

    // --- Additional WHERE clause tests ---

    @Test
    fun `where with iterable of records should match all`() {
        val repo = orm.entity(City::class)
        val city1 = City(id = 1, name = "Sun Paririe")
        val city2 = City(id = 2, name = "Madison")
        val result = repo.select().where(listOf(city1, city2)).resultList
        result shouldHaveSize 2
    }

    @Test
    fun `where with NOT_EQUALS operator should exclude matching entity`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath, NOT_EQUALS, "Madison").resultList
        result shouldHaveSize 5
    }

    @Test
    fun `where with LIKE operator should match pattern`() {
        // data.sql: Cities containing 'on': Madison, Monona (2 of 6).
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath, LIKE, "%on%").resultList
        result shouldHaveSize 2
    }

    // --- Model declaredColumns tests ---

    @Test
    fun `declaredColumns should return columns for owner model`() {
        val model = orm.entity(Owner::class).model
        val declared = model.declaredColumns
        val allColumns = model.columns
        // Owner has: id, firstName, lastName, address (inline), telephone, version
        // The inline Address expands into address (String) and city (FK -> city_id).
        // Verify both accessors work and return column data.
        declared.isNotEmpty() shouldBe true
        allColumns.isNotEmpty() shouldBe true
        // Verify known columns exist.
        allColumns.any { it.name == "id" } shouldBe true
        allColumns.any { it.name == "first_name" } shouldBe true
        allColumns.any { it.name == "last_name" } shouldBe true
        allColumns.any { it.name == "city_id" } shouldBe true
        allColumns.any { it.name == "version" } shouldBe true
    }

    // --- Model values tests ---

    @Test
    fun `model values should extract column values from entity`() {
        val model = orm.entity(City::class).model
        val city = City(id = 42, name = "TestCity")
        val values = model.values(city)
        values.size shouldBe 2
        val idEntry = values.entries.first { it.key.name == "id" }
        idEntry.value shouldBe 42
        val nameEntry = values.entries.first { it.key.name == "name" }
        nameEntry.value shouldBe "TestCity"
    }

    @Test
    fun `model isDefaultPrimaryKey should detect default pk`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(0) shouldBe true
        model.isDefaultPrimaryKey(1) shouldBe false
    }

    // --- Edge cases ---

    @Test
    fun `whereId with empty iterable should return no results`() {
        val result = orm.entity(City::class).select().whereId(emptyList()).resultList
        result shouldHaveSize 0
    }

    @Test
    fun `where with NOT_IN and empty list should return all results`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath, NOT_IN, emptyList<String>()).resultList
        result shouldHaveSize 6
    }

    @Test
    fun `inList with empty list should return no results`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath inList emptyList()).resultList
        result shouldHaveSize 0
    }

    @Test
    fun `limit zero should return no results`() {
        val cities = orm.entity(City::class).select().limit(0).resultList
        cities shouldHaveSize 0
    }

    @Test
    fun `resultFlow count should match resultCount`(): Unit = runBlocking {
        val repo = orm.entity(City::class)
        val flowCount = repo.select().resultFlow.count()
        val resultCount = repo.select().resultCount
        flowCount.toLong() shouldBe resultCount
    }

    // --- Query interface default methods ---

    @Test
    fun `build should return executable Query`() {
        val query = orm.entity(City::class).select().where(1).build()
        val result = query.getSingleResult(City::class)
        result.id shouldBe 1
        result.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query getResultFlow should return typed flow`(): Unit = runBlocking {
        val query = orm.entity(City::class).select().build()
        val cities = query.getResultFlow(City::class).toList()
        cities shouldHaveSize 6
    }

    @Test
    fun `query resultFlow should return untyped array flow`(): Unit = runBlocking {
        val query = orm.entity(City::class).select().build()
        val count = query.resultFlow.count()
        count shouldBe 6
    }

    @Test
    fun `query getResultList should return typed list`() {
        val query = orm.entity(City::class).select().build()
        val cities = query.getResultList(City::class)
        cities shouldHaveSize 6
    }

    @Test
    fun `query getSingleResult should return typed result`() {
        val query = orm.entity(City::class).select().where(1).build()
        val city = query.getSingleResult(City::class)
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query getOptionalResult should return typed result or null`() {
        val queryWithResult = orm.entity(City::class).select().where(1).build()
        val city = queryWithResult.getOptionalResult(City::class)
        city shouldNotBe null
        city!!.name shouldBe "Sun Paririe"

        val queryWithoutResult = orm.entity(City::class).select().where(999).build()
        val missing = queryWithoutResult.getOptionalResult(City::class)
        missing shouldBe null
    }

    @Test
    fun `query resultCount should count results`() {
        val query = orm.entity(City::class).select().build()
        query.resultCount shouldBe 6L
    }

    @Test
    fun `query versionAware should return version status`() {
        val cityQuery = orm.entity(City::class).select().build()
        // City has no @Version column, so version aware should be false.
        cityQuery.versionAware shouldBe false
    }

    @Test
    fun `query getRefList should return ref list`() {
        // getRefList maps each result row to a Ref by interpreting rows as the PK type.
        // The query must select only the PK column for this to work.
        val query = orm.query("SELECT id FROM city")
        val refs = query.getRefList(City::class, Integer::class)
        refs shouldHaveSize 6
        refs.forEach { ref ->
            ref.id() shouldNotBe null
        }
    }

    @Test
    fun `query getRefFlow should return ref flow`(): Unit = runBlocking {
        // getRefFlow maps each result row to a Ref by interpreting rows as the PK type.
        // The query must select only the PK column for this to work.
        val query = orm.query("SELECT id FROM city")
        val refs = query.getRefFlow(City::class, Integer::class).toList()
        refs shouldHaveSize 6
        refs.forEach { ref ->
            ref.id() shouldNotBe null
        }
    }

    // --- QueryBuilder: forShare ---

    @Test
    fun `forShare should build query with lock`() {
        // H2 does not support FOR SHARE syntax, so we verify the builder method is callable
        // and that execution throws a PersistenceException (SQL syntax error on H2).
        assertThrows<st.orm.PersistenceException> {
            orm.entity(City::class).select().where(1).forShare().singleResult
        }
    }

    // --- QueryBuilder: having ---

    @Test
    fun `having with template should filter grouped results`() {
        // data.sql: 10 owners across 6 cities. Per-city counts:
        //   city 1: 1 (Betty), city 2: 4 (George, Peter, Maria, David),
        //   city 3: 1 (Eduardo), city 4: 1 (Harold), city 5: 2 (Jean, Jeff), city 6: 1 (Carlos).
        // Only cities with count > 1: city 2 (4 owners), city 5 (2 owners).
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val counts = repo.selectCount().groupBy(cityPath).having { "COUNT(*) > ${t(1)}" }.resultList
        counts shouldHaveSize 2
    }

    // --- QueryBuilder: rightJoin ---

    @Test
    fun `rightJoin should include entities without matching join`() {
        val repo = orm.entity(Pet::class)
        // Right join Pet -> Owner includes all owners, even those whose pets do not reference them via this join path.
        // All 13 pets have types, and there are 6 pet types. Right join on PetType should include all pet types.
        val pets = repo.select().rightJoin(Owner::class).on(Pet::class).resultList
        // All 12 pets that have an owner (Pet 13 "Sly" has null owner, so right join excludes it
        // but includes all owners). Because it's a right join, owners without pets would also appear,
        // but all 10 owners have at least one pet. The result should be 12 (matching pet-owner pairs).
        pets shouldHaveSize 12
    }

    // --- QueryBuilder: crossJoin ---

    @Test
    fun `crossJoin should return cartesian product`() {
        // Cross join City x City produces 6*6 = 36 rows, but we only select from the primary table (City).
        // Limit to verify it produces more than 6 results.
        val repo = orm.entity(City::class)
        val count = repo.select().crossJoin(City::class).resultCount
        count shouldBe 36L
    }

    // --- QueryBuilder: whereBuilder advanced ---

    @Test
    fun `whereBuilder with subquery should filter entities`() {
        val ownerRepo = orm.entity(Owner::class)
        val results = ownerRepo.select().whereBuilder { exists(subquery(Pet::class)) }.resultList
        // All 10 owners have at least one pet.
        results shouldHaveSize 10
    }

    @Test
    fun `whereBuilder with notExists subquery should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val results = cityRepo.select().whereBuilder { notExists(subquery(Owner::class)) }.resultList
        // All 6 cities are referenced by at least one owner.
        results shouldHaveSize 0
    }

    @Test
    fun `whereBuilder with where using Ref should filter by FK reference`() {
        // data.sql: City 2 (Madison) has 4 owners: George (id=2), Peter (id=5), Maria (id=8), David (id=9).
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val results = repo.select().whereBuilder { where(cityPath, cityRef) }.resultList
        results shouldHaveSize 4
    }

    // --- QueryBuilder: append ---

    @Test
    fun `append with template should add raw SQL to query`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        // Use append to add a custom ORDER BY + LIMIT via the template builder API.
        val cities = repo.select().orderBy(idPath).append { "LIMIT ${t(3)}" }.resultList
        cities shouldHaveSize 3
        cities[0].id shouldBe 1
        cities[2].id shouldBe 3
    }
}
