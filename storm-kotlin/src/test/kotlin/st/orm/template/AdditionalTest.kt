package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Data
import st.orm.Metamodel
import st.orm.Operator.*
import st.orm.Ref
import st.orm.repository.*
import st.orm.template.model.*
import javax.sql.DataSource

/**
 * Broad coverage tests targeting uncovered default interface method implementations
 * for EntityRepository, ProjectionRepository, QueryTemplate, Query, and PreparedQuery.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class AdditionalTest(
    @Autowired val orm: ORMTemplate,
    @Autowired val dataSource: DataSource,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // EntityRepository: Ref-cursor slice methods (all uncovered)

    @Test
    fun `entity sliceAfter with Ref cursor should return next page`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 2)
        val slice = repo.sliceAfter(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfterRef with Ref cursor should return next page refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 2)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBefore with Ref cursor should return previous page`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 5)
        val slice = repo.sliceBefore(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBeforeRef with Ref cursor should return previous page refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 5)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfter with Ref cursor and WhereBuilder should filter`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfter with Ref cursor and PredicateBuilder should filter`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfterRef with Ref cursor and WhereBuilder should filter refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfterRef with Ref cursor and PredicateBuilder should filter refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBefore with Ref cursor and WhereBuilder should filter`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBefore with Ref cursor and PredicateBuilder should filter`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBeforeRef with Ref cursor and WhereBuilder should filter refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBeforeRef with Ref cursor and PredicateBuilder should filter refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<Owner, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    // Ref-cursor with sort metamodel
    @Test
    fun `entity sliceAfter with Ref cursor and sort should return sorted page`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val namePath = metamodel<Owner, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, namePath, "A", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceAfterRef with Ref cursor and sort should return sorted refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val namePath = metamodel<Owner, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, namePath, "A", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBefore with Ref cursor and sort should return sorted page`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val namePath = metamodel<Owner, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, namePath, "Z", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `entity sliceBeforeRef with Ref cursor and sort should return sorted refs`() {
        val repo = orm.entity(Owner::class)
        val cityKey = metamodel<Owner, City>(repo.model, "city_id").key()
        val namePath = metamodel<Owner, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, namePath, "Z", 10)
        slice.content.shouldNotBeEmpty()
    }

    // sliceBeforeRef with WhereBuilder/PredicateBuilder (no cursor)
    @Test
    fun `entity sliceBeforeRef with WhereBuilder should filter refs descending`() {
        val repo = orm.entity(City::class)
        val idKey = metamodel<City, Int>(repo.model, "id").key()
        val namePath = metamodel<City, String>(repo.model, "name")
        val slice = repo.sliceBeforeRef(idKey, 10) { where(namePath, LIKE, "M%") }
        slice.content shouldHaveSize 3
    }

    @Test
    fun `entity sliceBeforeRef with PredicateBuilder should filter refs descending`() {
        val repo = orm.entity(City::class)
        val idKey = metamodel<City, Int>(repo.model, "id").key()
        val namePath = metamodel<City, String>(repo.model, "name")
        val slice = repo.sliceBeforeRef(idKey, 10, namePath like "M%")
        slice.content shouldHaveSize 3
    }

    // findAllBy with Iterable (covers DefaultImpls for Iterable overload)
    @Test
    fun `entity findAllBy with iterable should return matching entities`() {
        val repo = orm.entity(City::class)
        val namePath = metamodel<City, String>(repo.model, "name")
        val cities = repo.findAllBy(namePath, listOf("Madison", "Windsor"))
        cities shouldHaveSize 2
    }

    // ProjectionRepository: Ref-cursor slice methods (all uncovered)

    @Test
    fun `projection sliceAfter with Ref cursor should return next page`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 2)
        val slice = repo.sliceAfter(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfterRef with Ref cursor should return next page refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 2)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBefore with Ref cursor should return previous page`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 5)
        val slice = repo.sliceBefore(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBeforeRef with Ref cursor should return previous page refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val cityRef = Ref.of(City::class.java, 5)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfter with Ref cursor and WhereBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfter with Ref cursor and PredicateBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfterRef with Ref cursor and WhereBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfterRef with Ref cursor and PredicateBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBefore with Ref cursor and WhereBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBefore with Ref cursor and PredicateBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBeforeRef with Ref cursor and WhereBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10) { where(lastNamePath, LIKE, "%") }
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBeforeRef with Ref cursor and PredicateBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    // Ref-cursor with sort metamodel for projection
    @Test
    fun `projection sliceAfter with Ref cursor and sort should return sorted page`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val namePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfter(cityKey, cityRef, namePath, "A", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfterRef with Ref cursor and sort should return sorted refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val namePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 1)
        val slice = repo.sliceAfterRef(cityKey, cityRef, namePath, "A", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBefore with Ref cursor and sort should return sorted page`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val namePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBefore(cityKey, cityRef, namePath, "Z", 10)
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBeforeRef with Ref cursor and sort should return sorted refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val namePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val cityRef = Ref.of(City::class.java, 6)
        val slice = repo.sliceBeforeRef(cityKey, cityRef, namePath, "Z", 10)
        slice.content.shouldNotBeEmpty()
    }

    // ProjectionRepository uncovered: sliceBeforeRef with WhereBuilder/PredicateBuilder (no cursor)
    @Test
    fun `projection sliceBeforeRef with WhereBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
    }

    @Test
    fun `projection sliceBeforeRef with PredicateBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 10, lastNamePath eq "Davis")
        slice.content shouldHaveSize 2
    }

    // ProjectionRepository: select with template builder (uncovered in DefaultImpls)
    @Test
    fun `projection select with template builder should return typed results`() {
        val repo = orm.projection(OwnerView::class)
        val results = repo.select(OwnerView::class) { t(Templates.select(OwnerView::class)) }.resultList
        results shouldHaveSize 10
    }

    // ProjectionRepository: findAllBy with Iterable (covers DefaultImpls)
    @Test
    fun `projection findAllBy with iterable should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val projections = repo.findAllBy(lastNamePath, listOf("Davis", "Franklin"))
        projections shouldHaveSize 3
    }

    // ProjectionRepository: uncovered convenience methods
    @Test
    fun `projection exists with WhereBuilder should return true for matching value`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        repo.exists { where(lastNamePath, EQUALS, "Davis") } shouldBe true
    }

    @Test
    fun `projection exists with PredicateBuilder should return true for matching value`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        repo.exists(lastNamePath eq "Davis") shouldBe true
    }

    @Test
    fun `projection findBy should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val owner = repo.findBy(lastNamePath, "Franklin")
        owner.shouldNotBeNull()
        owner.lastName shouldBe "Franklin"
    }

    @Test
    fun `projection getBy should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val owner = repo.getBy(lastNamePath, "Franklin")
        owner.lastName shouldBe "Franklin"
    }

    @Test
    fun `projection findRefBy should return ref for matching value`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val ref = repo.findRefBy<OwnerView, Int, String>(lastNamePath, "Franklin")
        ref.shouldNotBeNull()
    }

    @Test
    fun `projection getRefBy should return ref for matching value`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val ref = repo.getRefBy(lastNamePath, "Franklin")
        ref.shouldNotBeNull()
    }

    @Test
    fun `projection findAllBy should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val owners = repo.findAllBy(lastNamePath, "Davis")
        owners shouldHaveSize 2
    }

    @Test
    fun `projection selectBy should return matching projections flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val owners = repo.selectBy(lastNamePath, "Davis").toList()
        owners shouldHaveSize 2
    }

    @Test
    fun `projection findAllRefBy with single value should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.findAllRefBy(lastNamePath, "Davis")
        refs shouldHaveSize 2
    }

    @Test
    fun `projection selectRefBy should return matching refs flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.selectRefBy(lastNamePath, "Davis").toList()
        refs shouldHaveSize 2
    }

    // ProjectionRepository: sliceAfter/sliceBefore with PredicateBuilder (non-Ref cursor, uncovered variants)
    @Test
    fun `projection sliceAfter with cursor and PredicateBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfter(idKey, 1, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceAfterRef with cursor and PredicateBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfterRef(idKey, 1, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBefore with cursor and PredicateBuilder should filter`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, 10, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    @Test
    fun `projection sliceBeforeRef with cursor and PredicateBuilder should filter refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 10, 10, lastNamePath like "%")
        slice.content.shouldNotBeEmpty()
    }

    // ORMTemplate.DefaultImpls: subquery, selectFrom, model, query

    @Test
    fun `subquery with single type should return query builder`() {
        val builder = orm.subquery(City::class)
        builder.shouldNotBeNull()
    }

    @Test
    fun `subquery with two types should return query builder`() {
        val builder = orm.subquery(City::class, City::class)
        builder.shouldNotBeNull()
    }

    @Test
    fun `subquery with template builder should return query builder`() {
        val builder = orm.subquery(City::class) { t(Templates.select(City::class)) }
        builder.shouldNotBeNull()
    }

    @Test
    fun `selectFrom with single type should return query builder`() {
        val results = orm.selectFrom(City::class).resultList
        results shouldHaveSize 6
    }

    @Test
    fun `selectFrom with two types should return query builder`() {
        val results = orm.selectFrom(City::class, City::class).resultList
        results shouldHaveSize 6
    }

    @Test
    fun `selectFrom with template builder should return query builder`() {
        val results = orm.selectFrom(City::class, City::class) { t(Templates.select(City::class)) }.resultList
        results shouldHaveSize 6
    }

    @Test
    fun `model with single type should return model info`() {
        val model = orm.model(City::class)
        model.shouldNotBeNull()
        model.name shouldBe "city"
    }

    @Test
    fun `query with template builder should execute query`() {
        val result = orm.query { "SELECT COUNT(*) FROM city" }.singleResult
        result shouldBe arrayOf(6L)
    }

    // Query.DefaultImpls: typed result methods and PreparedQuery

    @Test
    fun `query getSingleResult with type should return typed result`() {
        val city = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(1)}" }
            .getSingleResult(City::class)
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query getOptionalResult with type should return typed result`() {
        val city = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(1)}" }
            .getOptionalResult(City::class)
        city.shouldNotBeNull()
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query getOptionalResult with type should return null for no match`() {
        val city = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(9999)}" }
            .getOptionalResult(City::class)
        city.shouldBeNull()
    }

    @Test
    fun `query getResultList with type should return typed list`() {
        val cities = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
            .getResultList(City::class)
        cities shouldHaveSize 6
    }

    @Test
    fun `query getResultFlow with type should return typed flow`(): Unit = runBlocking {
        val count = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
            .getResultFlow(City::class)
            .count()
        count shouldBe 6
    }

    @Test
    fun `query resultFlow should return flow of arrays`(): Unit = runBlocking {
        val count = orm.query("SELECT id, name FROM city")
            .resultFlow
            .count()
        count shouldBe 6
    }

    @Test
    fun `query getRefList should return ref list`() {
        val refs = orm.query("SELECT id FROM city")
            .getRefList(City::class, Int::class)
        refs shouldHaveSize 6
    }

    @Test
    fun `query getRefFlow should return ref flow`(): Unit = runBlocking {
        val count = orm.query("SELECT id FROM city")
            .getRefFlow(City::class, Int::class)
            .count()
        count shouldBe 6
    }

    // PreparedQuery default methods
    @Test
    fun `prepared query getSingleResult with type should return typed result`() {
        orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(1)}" }.prepare().use { preparedQuery ->
            val city = preparedQuery.getSingleResult(City::class)
            city.name shouldBe "Sun Paririe"
        }
    }

    @Test
    fun `prepared query getOptionalResult with type should return typed result`() {
        orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(1)}" }.prepare().use { preparedQuery ->
            val city = preparedQuery.getOptionalResult(City::class)
            city.shouldNotBeNull()
        }
    }

    @Test
    fun `prepared query getResultList with type should return typed list`() {
        orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }.prepare().use { preparedQuery ->
            val cities = preparedQuery.getResultList(City::class)
            cities shouldHaveSize 6
        }
    }

    @Test
    fun `prepared query getResultFlow with type should return typed flow`(): Unit = runBlocking {
        orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }.prepare().use { preparedQuery ->
            val count = preparedQuery.getResultFlow(City::class).count()
            count shouldBe 6
        }
    }

    @Test
    fun `prepared query singleResult should return array`() {
        orm.query("SELECT COUNT(*) FROM city").prepare().use { preparedQuery ->
            val result = preparedQuery.singleResult
            result shouldBe arrayOf(6L)
        }
    }

    @Test
    fun `prepared query optionalResult should return array`() {
        orm.query("SELECT COUNT(*) FROM city").prepare().use { preparedQuery ->
            val result = preparedQuery.optionalResult
            result.shouldNotBeNull()
        }
    }

    @Test
    fun `prepared query resultList should return list of arrays`() {
        orm.query("SELECT id, name FROM city").prepare().use { preparedQuery ->
            val results = preparedQuery.resultList
            results shouldHaveSize 6
        }
    }

    @Test
    fun `prepared query resultFlow should return flow of arrays`(): Unit = runBlocking {
        orm.query("SELECT id, name FROM city").prepare().use { preparedQuery ->
            val count = preparedQuery.resultFlow.count()
            count shouldBe 6
        }
    }

    @Test
    fun `prepared query resultCount should return count`() {
        orm.query("SELECT id FROM city").prepare().use { preparedQuery ->
            val count = preparedQuery.resultCount
            count shouldBe 6L
        }
    }

    @Test
    fun `prepared query getRefList should return ref list`() {
        orm.query("SELECT id FROM city").prepare().use { preparedQuery ->
            val refs = preparedQuery.getRefList(City::class, Int::class)
            refs shouldHaveSize 6
        }
    }

    @Test
    fun `prepared query getRefFlow should return ref flow`(): Unit = runBlocking {
        orm.query("SELECT id FROM city").prepare().use { preparedQuery ->
            val count = preparedQuery.getRefFlow(City::class, Int::class).count()
            count shouldBe 6
        }
    }

    // PredicateBuilderFactory: createRef / createRefWithId

    @Test
    fun `createRef predicate builder should return PredicateBuilder`() {
        val metamodel = Metamodel.of<Pet, Owner>(Pet::class.java, "owner")
        val refs = listOf(Ref.of(Owner::class.java, 1), Ref.of(Owner::class.java, 2))
        val predicate = st.orm.template.impl.createRef<Pet, Pet, Owner>(
            metamodel,
            st.orm.Operator.IN,
            refs,
        )
        predicate.shouldNotBeNull()
        predicate.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    @Test
    fun `createRefWithId predicate builder should return PredicateBuilder`() {
        val metamodel = Metamodel.of<Pet, Owner>(Pet::class.java, "owner")
        val refs = listOf(Ref.of(Owner::class.java, 1), Ref.of(Owner::class.java, 2))
        val predicate = st.orm.template.impl.createRefWithId<Pet, Pet, Int, Owner>(
            metamodel,
            st.orm.Operator.IN,
            refs,
        )
        predicate.shouldNotBeNull()
        predicate.shouldBeInstanceOf<PredicateBuilder<*, *, *>>()
    }

    // QueryTemplate.DefaultImpls: query with TemplateBuilder

    @Test
    fun `query with TemplateBuilder should return Query`() {
        val result = orm.query { "SELECT COUNT(*) FROM city" }.singleResult
        result shouldBe arrayOf(6L)
    }
}
