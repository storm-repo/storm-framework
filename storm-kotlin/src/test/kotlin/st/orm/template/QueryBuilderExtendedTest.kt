package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Data
import st.orm.JoinType
import st.orm.Metamodel
import st.orm.Operator.*
import st.orm.PersistenceException
import st.orm.Ref
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class QueryBuilderExtendedTest(
    @Autowired val orm: ORMTemplate,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // ======================================================================
    // TemplateBuilder-based where overloads
    // ======================================================================

    @Test
    fun `where with TemplateBuilder should filter results`() {
        // Use a TemplateBuilder lambda to add a WHERE clause filtering by name.
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.select().where { "${t(Templates.column(namePath))} = ${t("Madison")}" }.resultList
        cities shouldHaveSize 1
        cities[0].name shouldBe "Madison"
    }

    @Test
    fun `where with TemplateString should filter results`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val templateString = TemplateString.raw { "${t(Templates.column(namePath))} = ${t("Windsor")}" }
        val cities = repo.select().where(templateString).resultList
        cities shouldHaveSize 1
        cities[0].name shouldBe "Windsor"
    }

    // ======================================================================
    // whereRef overloads on QueryBuilder
    // ======================================================================

    @Test
    fun `where with Ref should filter by foreign key reference`() {
        // data.sql: City 2 (Madison) has 4 owners: George (id=2), Peter (id=5), Maria (id=8), David (id=9).
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val owners = repo.select().where(cityPath, cityRef).resultList
        owners shouldHaveSize 4
    }

    @Test
    fun `whereRef with iterable of refs should filter entities`() {
        // Filter cities by a list of refs: city 1 and city 3.
        val repo = orm.entity(City::class)
        val ref1: Ref<City> = Ref.of(City::class.java, 1)
        val ref3: Ref<City> = Ref.of(City::class.java, 3)
        val cities = repo.select().whereRef(listOf(ref1, ref3)).resultList
        cities shouldHaveSize 2
    }

    @Test
    fun `whereRef with metamodel path and iterable of refs should filter entities`() {
        // Filter owners by city refs: city 1 and city 3.
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val ref1: Ref<City> = Ref.of(City::class.java, 1)
        val ref3: Ref<City> = Ref.of(City::class.java, 3)
        val owners = repo.select().whereRef(cityPath, listOf(ref1, ref3)).resultList
        // City 1: Betty (id=1), City 3: Eduardo (id=3)
        owners shouldHaveSize 2
    }

    // ======================================================================
    // TemplateBuilder-based join overloads
    // ======================================================================

    @Test
    fun `innerJoin with TemplateString and alias should join and filter`() {
        // Use City (which has no FK relations) and join to pet_type to avoid alias conflicts.
        val repo = orm.entity(City::class)
        val templateString = TemplateString.raw("pet_type")
        val count = repo.select().innerJoin(templateString, "pt").on { "pt.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        // Pet types have IDs 0-5, cities have IDs 1-6. Matching IDs: 1,2,3,4,5 = 5.
        count shouldBe 5L
    }

    @Test
    fun `innerJoin with TemplateBuilder and alias should join and filter`() {
        val repo = orm.entity(City::class)
        val templateBuilder: TemplateBuilder = { "pet_type" }
        val count = repo.select().innerJoin(templateBuilder, "pt").on { "pt.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        // Pet types have IDs 0-5, cities have IDs 1-6. Matching IDs: 1,2,3,4,5 = 5.
        count shouldBe 5L
    }

    @Test
    fun `leftJoin with TemplateString and alias should include all rows`() {
        val repo = orm.entity(City::class)
        val templateString = TemplateString.raw("vet")
        val count = repo.select().leftJoin(templateString, "v").on { "v.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        // 6 cities, all have matching vet ids (1..6), left join keeps all.
        count shouldBe 6L
    }

    @Test
    fun `leftJoin with TemplateBuilder and alias should include all rows`() {
        val repo = orm.entity(City::class)
        val templateBuilder: TemplateBuilder = { "vet" }
        val count = repo.select().leftJoin(templateBuilder, "v").on { "v.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        count shouldBe 6L
    }

    @Test
    fun `rightJoin with TemplateString and alias should include join-side rows`() {
        val repo = orm.entity(City::class)
        val templateString = TemplateString.raw("vet")
        val count = repo.select().rightJoin(templateString, "v").on { "v.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        // 6 vets, all have matching city ids (1..6), right join keeps all.
        count shouldBe 6L
    }

    @Test
    fun `rightJoin with TemplateBuilder and alias should include join-side rows`() {
        val repo = orm.entity(City::class)
        val templateBuilder: TemplateBuilder = { "vet" }
        val count = repo.select().rightJoin(templateBuilder, "v").on { "v.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        count shouldBe 6L
    }

    @Test
    fun `crossJoin with TemplateString should produce cartesian product`() {
        val repo = orm.entity(City::class)
        val templateString = TemplateString.raw("pet_type")
        val count = repo.select().crossJoin(templateString).resultCount
        // 6 cities x 6 pet types = 36
        count shouldBe 36L
    }

    @Test
    fun `crossJoin with TemplateBuilder should produce cartesian product`() {
        val repo = orm.entity(City::class)
        val count = repo.select().crossJoin { "pet_type" }.resultCount
        // 6 cities x 6 pet types = 36
        count shouldBe 36L
    }

    @Test
    fun `join with JoinType and TemplateString should work`() {
        val repo = orm.entity(City::class)
        val templateString = TemplateString.raw("pet_type")
        val count = repo.select().join(JoinType.inner(), templateString, "pt").on { "pt.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        // Pet types have IDs 0-5, cities have IDs 1-6. Matching IDs: 1,2,3,4,5 = 5.
        count shouldBe 5L
    }

    @Test
    fun `join with JoinType and TemplateBuilder should work`() {
        val repo = orm.entity(City::class)
        val templateBuilder: TemplateBuilder = { "pet_type" }
        val count = repo.select().join(JoinType.left(), templateBuilder, "pt").on { "pt.id = ${t(Templates.alias(City::class))}.id" }.resultCount
        count shouldBe 6L
    }

    @Test
    fun `join with JoinType and KClass and alias should work`() {
        // Use Pet joining Owner, which is the existing typed join pattern.
        val repo = orm.entity(Pet::class)
        val count = repo.select().join(JoinType.inner(), Owner::class, "").on(Pet::class).resultCount
        // 12 pets have owners.
        count shouldBe 12L
    }

    // ======================================================================
    // orderBy with TemplateBuilder and TemplateString
    // ======================================================================

    @Test
    fun `orderBy with TemplateBuilder should sort results`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy { "${t(Templates.column(idPath))} ASC" }.resultList
        cities[0].id shouldBe 1
        cities[5].id shouldBe 6
    }

    @Test
    fun `orderBy with TemplateString should sort results`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val templateString = TemplateString.raw { t(Templates.column(idPath)) }
        val cities = repo.select().orderBy(templateString).resultList
        cities[0].id shouldBe 1
    }

    @Test
    fun `orderByDescending with TemplateBuilder should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescending { t(Templates.column(idPath)) }.resultList
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    @Test
    fun `orderByDescending with TemplateString should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val templateString = TemplateString.raw { t(Templates.column(idPath)) }
        val cities = repo.select().orderByDescending(templateString).resultList
        cities[0].id shouldBe 6
    }

    @Test
    fun `orderByAny with metamodel should sort results`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByAny(idPath).resultList
        cities[0].id shouldBe 1
        cities[5].id shouldBe 6
    }

    @Test
    fun `orderByDescending with vararg metamodels should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescending(idPath).resultList
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    @Test
    fun `orderByDescendingAny with single metamodel should sort descending`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderByDescendingAny(idPath).resultList
        cities[0].id shouldBe 6
        cities[5].id shouldBe 1
    }

    @Test
    fun `orderByDescendingAny with vararg metamodels should sort descending`() {
        val repo = orm.entity(Owner::class)
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val owners = repo.select().orderByDescendingAny(lastNamePath, firstNamePath).resultList
        // Verify first result has alphabetically last-last-name (Schroeder > Rodriquez > ...)
        owners shouldHaveSize 10
    }

    // ======================================================================
    // groupBy with TemplateBuilder and TemplateString
    // ======================================================================

    @Test
    fun `groupBy with TemplateBuilder should group results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val counts = repo.selectCount().groupBy { t(Templates.column(cityPath)) }.resultList
        counts shouldHaveSize 6
    }

    @Test
    fun `groupBy with TemplateString should group results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val templateString = TemplateString.raw { t(Templates.column(cityPath)) }
        val counts = repo.selectCount().groupBy(templateString).resultList
        counts shouldHaveSize 6
    }

    @Test
    fun `groupByAny with metamodel should group results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val counts = repo.selectCount().groupByAny(cityPath).resultList
        counts shouldHaveSize 6
    }

    @Test
    fun `groupByAny with empty vararg should throw PersistenceException`() {
        val repo = orm.entity(Owner::class)
        assertThrows<PersistenceException> {
            repo.selectCount().groupByAny().resultList
        }
    }

    @Test
    fun `orderByAny with empty vararg should throw PersistenceException`() {
        val repo = orm.entity(City::class)
        assertThrows<PersistenceException> {
            repo.select().orderByAny().resultList
        }
    }

    @Test
    fun `orderByDescendingAny with empty vararg should throw PersistenceException`() {
        val repo = orm.entity(City::class)
        assertThrows<PersistenceException> {
            repo.select().orderByDescendingAny().resultList
        }
    }

    // ======================================================================
    // having with TemplateBuilder and TemplateString
    // ======================================================================

    @Test
    fun `having with TemplateBuilder should filter grouped results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val counts = repo.selectCount().groupBy(cityPath).having { "COUNT(*) > ${t(1)}" }.resultList
        // Only cities with count > 1: city 2 (4 owners), city 5 (2 owners).
        counts shouldHaveSize 2
    }

    @Test
    fun `having with TemplateString should filter grouped results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        val templateString = TemplateString.raw { "COUNT(*) > ${t(1)}" }
        val counts = repo.selectCount().groupBy(cityPath).having(templateString).resultList
        counts shouldHaveSize 2
    }

    // ======================================================================
    // append with TemplateBuilder and TemplateString
    // ======================================================================

    @Test
    fun `append with TemplateBuilder should add clause`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).append { "" }.resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 1
    }

    @Test
    fun `append with TemplateString should add clause`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val cities = repo.select().orderBy(idPath).append(TemplateString.raw("")).resultList
        cities shouldHaveSize 6
        cities[0].id shouldBe 1
    }

    // ======================================================================
    // forLock with TemplateString
    // ======================================================================

    @Test
    fun `forLock with TemplateString should apply lock mode`() {
        val templateString = TemplateString.raw("FOR UPDATE")
        val city = orm.entity(City::class).select().where(1).forLock(templateString).singleResult
        city.id shouldBe 1
    }

    @Test
    fun `forLock with TemplateBuilder should apply lock mode`() {
        val city = orm.entity(City::class).select().where(1).forLock { "FOR UPDATE" }.singleResult
        city.id shouldBe 1
    }

    // ======================================================================
    // PredicateBuilder: andAny / orAny
    // ======================================================================

    @Test
    fun `andAny should combine predicates across different types`() {
        val repo = orm.entity(Owner::class)
        val firstNamePath = metamodel<Owner, String>(repo.model, "first_name")
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        // Use andAny to combine a predicate with another typed predicate.
        val result = repo.select().whereBuilder {
            where(firstNamePath, EQUALS, "Betty") andAny where(lastNamePath, EQUALS, "Davis")
        }.resultList
        result shouldHaveSize 1
        result[0].firstName shouldBe "Betty"
    }

    @Test
    fun `orAny should combine predicates across different types`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") orAny where(namePath, EQUALS, "Windsor")
        }.resultList
        result shouldHaveSize 2
    }

    // ======================================================================
    // PredicateBuilder: and/or with TemplateBuilder and TemplateString
    // ======================================================================

    @Test
    fun `predicate and with TemplateBuilder should combine conditions`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") and { "${t(Templates.column(metamodel<City, Int>(repo.model, "id")))} = ${t(2)}" }
        }.resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Madison"
    }

    @Test
    fun `predicate and with TemplateString should combine conditions`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val templateString = TemplateString.raw { "${t(Templates.column(metamodel<City, Int>(repo.model, "id")))} = ${t(2)}" }
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") and templateString
        }.resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Madison"
    }

    @Test
    fun `predicate or with TemplateBuilder should combine conditions`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") or { "${t(Templates.column(namePath))} = ${t("Windsor")}" }
        }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `predicate or with TemplateString should combine conditions`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val templateString = TemplateString.raw { "${t(Templates.column(namePath))} = ${t("Windsor")}" }
        val result = repo.select().whereBuilder {
            where(namePath, EQUALS, "Madison") or templateString
        }.resultList
        result shouldHaveSize 2
    }

    // ======================================================================
    // WhereBuilder: whereAny overloads
    // ======================================================================

    @Test
    fun `whereAny with record should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val city = City(id = 2, name = "Madison")
        val result = cityRepo.select().whereBuilder { whereAny(city as Data) }.resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Madison"
    }

    @Test
    fun `whereAny with iterable of records should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val city1 = City(id = 1, name = "Sun Paririe") as Data
        val city2 = City(id = 2, name = "Madison") as Data
        val result = cityRepo.select().whereBuilder { whereAny(listOf(city1, city2)) }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `whereAnyRef with single ref should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val ref: Ref<City> = Ref.of(City::class.java, 3)

        @Suppress("UNCHECKED_CAST")
        val result = cityRepo.select().whereBuilder { whereAnyRef(ref as Ref<out Data>) }.resultList
        result shouldHaveSize 1
        result[0].name shouldBe "McFarland"
    }

    @Test
    fun `whereAnyRef with iterable of refs should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val ref1: Ref<City> = Ref.of(City::class.java, 1)
        val ref2: Ref<City> = Ref.of(City::class.java, 4)

        @Suppress("UNCHECKED_CAST")
        val result = cityRepo.select().whereBuilder { whereAnyRef(listOf(ref1 as Ref<out Data>, ref2 as Ref<out Data>)) }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `whereRef with single ref should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val ref: Ref<City> = Ref.of(City::class.java, 5)
        val result = cityRepo.select().whereBuilder { whereRef(ref) }.resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Monona"
    }

    @Test
    fun `whereRef with iterable of refs via WhereBuilder should filter entities`() {
        val cityRepo = orm.entity(City::class)
        val ref1: Ref<City> = Ref.of(City::class.java, 2)
        val ref5: Ref<City> = Ref.of(City::class.java, 5)
        val result = cityRepo.select().whereBuilder { whereRef(listOf(ref1, ref5)) }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `whereBuilder with where TemplateBuilder should filter`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            where { "${t(Templates.column(namePath))} = ${t("Madison")}" }
        }.resultList
        result shouldHaveSize 1
    }

    @Test
    fun `whereBuilder with where TemplateString should filter`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val templateString = TemplateString.raw { "${t(Templates.column(namePath))} = ${t("McFarland")}" }
        val result = repo.select().whereBuilder {
            where(templateString)
        }.resultList
        result shouldHaveSize 1
    }

    // ======================================================================
    // QueryBuilder: whereAny with PredicateBuilder
    // ======================================================================

    @Test
    fun `whereAny with PredicateBuilder should filter entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereAny(namePath eq "Madison").resultList
        result shouldHaveSize 1
    }

    @Test
    fun `whereAnyBuilder should use WhereBuilder from any type`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereAnyBuilder {
            where(namePath, EQUALS, "Madison")
        }.resultList
        result shouldHaveSize 1
    }

    // ======================================================================
    // QueryBuilder: where(record: T) delegating to whereBuilder
    // ======================================================================

    @Test
    fun `where with ref should filter by PK`() {
        val repo = orm.entity(City::class)
        val ref: Ref<City> = Ref.of(City::class.java, 4)
        val result = repo.select().where(ref).resultList
        result shouldHaveSize 1
        result[0].name shouldBe "Windsor"
    }

    @Test
    fun `where with iterable of records should delegate to whereBuilder`() {
        val repo = orm.entity(City::class)
        val city1 = City(id = 1, name = "Sun Paririe")
        val city3 = City(id = 3, name = "McFarland")
        val result = repo.select().where(listOf(city1, city3)).resultList
        result shouldHaveSize 2
    }

    @Test
    fun `where with path operator and iterable should filter`() {
        // Use the where(path, IN, Iterable) overload
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().where(namePath, IN, listOf("Madison", "Windsor")).resultList
        result shouldHaveSize 2
    }

    // ======================================================================
    // Slice-based pagination
    // ======================================================================

    @Test
    fun `slice with simple size should return correct page`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `slice with size greater than total should not have next`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(10)
        slice.content shouldHaveSize 6
        slice.hasNext shouldBe false
    }

    @Test
    fun `slice with exact size should not have next`() {
        val repo = orm.entity(City::class)
        val idPath = metamodel<City, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(6)
        slice.content shouldHaveSize 6
        slice.hasNext shouldBe false
    }

    @Test
    fun `slice with zero size should throw IllegalArgumentException`() {
        val repo = orm.entity(City::class)
        assertThrows<IllegalArgumentException> {
            repo.select().slice(0)
        }
    }

    // ======================================================================
    // selectFrom/deleteFrom with select type
    // ======================================================================

    @Test
    fun `selectFrom with entity and select type should work`() {
        val cities = orm.selectFrom(City::class, City::class).resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `selectFrom with TemplateBuilder should work`() {
        val cities = orm.selectFrom(City::class, City::class) { t(City::class) }.resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `selectFrom with TemplateString should work`() {
        val templateString = TemplateString.wrap(City::class)
        val cities = orm.selectFrom(City::class, City::class, templateString).resultList
        cities shouldHaveSize 6
    }

    // ======================================================================
    // Query: prepare and PreparedQuery operations
    // ======================================================================

    @Test
    fun `prepare should return PreparedQuery that can be closed`() {
        val preparedQuery = orm.entity(City::class).select().where(1).prepare()
        preparedQuery.use { query ->
            val city = query.getSingleResult(City::class)
            city.id shouldBe 1
        }
    }

    @Test
    fun `prepared query addBatch and getGeneratedKeys should work for insert`() {
        val bindVars = orm.createBindVars()
        val insertQuery = orm.query { "INSERT INTO ${t(City::class)} VALUES ${t(bindVars)}" }
        insertQuery.prepare().use { prepared ->
            prepared.addBatch(City(name = "PreparedA"))
            prepared.addBatch(City(name = "PreparedB"))
            prepared.executeBatch()
            val keys = prepared.getGeneratedKeys(Int::class).toList()
            keys shouldHaveSize 2
            keys.all { it > 0 } shouldBe true
        }
    }

    // ======================================================================
    // QueryTemplate: query with TemplateBuilder
    // ======================================================================

    @Test
    fun `query with TemplateBuilder should execute`() {
        val query = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val cities = query.getResultList(City::class)
        cities shouldHaveSize 6
    }

    @Test
    fun `query with TemplateString should execute`() {
        val templateString = TemplateString.raw { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val query = orm.query(templateString)
        val cities = query.getResultList(City::class)
        cities shouldHaveSize 6
    }

    // ======================================================================
    // QueryTemplate: model and ref methods
    // ======================================================================

    @Test
    fun `model should return type metadata`() {
        val model = orm.model(City::class)
        model.name shouldBe "city"
        model.type shouldBe City::class
    }

    @Test
    fun `model with requirePrimaryKey true should work for entity types`() {
        val model = orm.model(City::class, true)
        model.name shouldBe "city"
    }

    @Test
    fun `ref with type and id should create ref`() {
        val ref = orm.ref(City::class, 1)
        ref.id() shouldBe 1
    }

    @Test
    fun `ref with record and id should create loaded ref`() {
        val city = City(id = 42, name = "Test")
        val ref = orm.ref(city, 42)
        ref.id() shouldBe 42
    }

    @Test
    fun `createBindVars should return non-null`() {
        val bindVars = orm.createBindVars()
        bindVars shouldBe bindVars // non-null check
    }

    // ======================================================================
    // WhereBuilder: whereAny with path, operator, vararg
    // ======================================================================

    @Test
    fun `whereAny with path operator and vararg should filter`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            whereAny(namePath, EQUALS, "Madison")
        }.resultList
        result shouldHaveSize 1
    }

    @Test
    fun `whereAny with path operator and iterable should filter`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            whereAny(namePath, IN, listOf("Madison", "Windsor"))
        }.resultList
        result shouldHaveSize 2
    }

    @Test
    fun `whereAny with path and ref should filter by FK`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val result = repo.select().whereBuilder {
            @Suppress("UNCHECKED_CAST")
            whereAny(cityPath as Metamodel<*, City>, cityRef)
        }.resultList
        result shouldHaveSize 4
    }

    @Test
    fun `whereAnyRef with path and iterable of refs should filter`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, City>(repo.model, "city_id")
        val ref1: Ref<City> = Ref.of(City::class.java, 1)
        val ref3: Ref<City> = Ref.of(City::class.java, 3)
        val result = repo.select().whereBuilder {
            @Suppress("UNCHECKED_CAST")
            whereAnyRef(cityPath as Metamodel<*, City>, listOf(ref1, ref3))
        }.resultList
        result shouldHaveSize 2
    }

    // ======================================================================
    // WhereBuilder: where(path, record) and whereAny(path, record) default methods
    // ======================================================================

    @Test
    fun `whereAny path with record should use EQUALS operator`() {
        val repo = orm.entity(City::class)
        val city = City(id = 2, name = "Madison")
        val idPath = metamodel<City, City>(repo.model, "id")
        // This tests the whereAny(path, EQUALS, record) default method.
        val result = repo.select().whereBuilder {
            @Suppress("UNCHECKED_CAST")
            whereAny(idPath as Metamodel<*, City>, city)
        }.resultList
        result shouldHaveSize 1
    }

    // ======================================================================
    // WhereBuilder: whereAny with iterable of records through path
    // ======================================================================

    @Test
    fun `whereAny path with iterable of records should use IN operator`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val result = repo.select().whereBuilder {
            @Suppress("UNCHECKED_CAST")
            whereAny(namePath as Metamodel<*, String>, IN, listOf("Madison", "McFarland"))
        }.resultList
        result shouldHaveSize 2
    }

    // ======================================================================
    // resultFlow from QueryBuilder
    // ======================================================================

    @Test
    fun `resultFlow should return all results as flow`(): Unit = runBlocking {
        val count = orm.entity(City::class).select().resultFlow.count()
        count shouldBe 6
    }

    // ======================================================================
    // executeUpdate
    // ======================================================================

    @Test
    fun `executeUpdate should return count of affected rows`() {
        val repo = orm.entity(Visit::class)
        val count = repo.delete().unsafe().executeUpdate()
        count shouldBe 14
    }

    // ======================================================================
    // having with metamodel path
    // ======================================================================

    @Test
    fun `having with metamodel path should filter grouped results`() {
        val repo = orm.entity(Owner::class)
        val cityPath = metamodel<Owner, Any>(repo.model, "city_id")
        // Use the having(path, operator, value) overload that delegates to havingAny.
        val counts = repo.selectCount().groupBy(cityPath)
            .having { "COUNT(*) >= ${t(2)}" }
            .resultList
        // Cities with 2+ owners: city 2 (4 owners), city 5 (2 owners).
        counts shouldHaveSize 2
    }
}
