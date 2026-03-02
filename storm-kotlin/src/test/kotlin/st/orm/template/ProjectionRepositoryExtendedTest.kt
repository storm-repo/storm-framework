package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import st.orm.NoResultException
import st.orm.Operator.*
import st.orm.Ref
import st.orm.repository.*
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class ProjectionRepositoryExtendedTest(
    @Autowired val orm: ORMTemplate,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // ======================================================================
    // ProjectionRepository: findBy/getBy with Ref value
    // ======================================================================

    @Test
    fun `findBy with field and ref value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val view = repo.findBy(cityPath, cityRef)
        view.shouldNotBeNull()
        // City 1 has one owner: Betty.
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findBy with field and ref value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 999)
        val view = repo.findBy(cityPath, cityRef)
        view.shouldBeNull()
    }

    @Test
    fun `getBy with field and ref value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val view = repo.getBy(cityPath, cityRef)
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getBy with field and ref value should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 999)
        assertThrows<NoResultException> {
            repo.getBy(cityPath, cityRef)
        }
    }

    // ======================================================================
    // ProjectionRepository: findAllBy/selectBy with Ref and iterable of Ref
    // ======================================================================

    @Test
    fun `findAllBy with field and ref value should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val views = repo.findAllBy(cityPath, cityRef)
        // City 2 has 4 owners.
        views shouldHaveSize 4
    }

    @Test
    fun `findAllByRef with metamodel and iterable of refs should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(Ref.of(City::class.java, 1), Ref.of(City::class.java, 3))
        val views = repo.findAllByRef(cityPath, refs)
        // City 1: Betty, City 3: Eduardo
        views shouldHaveSize 2
    }

    @Test
    fun `selectBy with field and ref value should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val count = repo.selectBy(cityPath, cityRef).count()
        count shouldBe 4
    }

    @Test
    fun `selectByRef with metamodel and iterable of refs should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(Ref.of(City::class.java, 1), Ref.of(City::class.java, 3))
        val count = repo.selectByRef(cityPath, refs).count()
        count shouldBe 2
    }

    // ======================================================================
    // ProjectionRepository: findRefBy/getRefBy with Ref value
    // ======================================================================

    @Test
    fun `findRefBy with field and ref value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val ref = repo.findRefBy(cityPath, cityRef)
        ref.shouldNotBeNull()
    }

    @Test
    fun `findRefBy with field and ref value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 999)
        val ref = repo.findRefBy(cityPath, cityRef)
        ref.shouldBeNull()
    }

    @Test
    fun `selectRefBy with field and ref value should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val refs = repo.selectRefBy(cityPath, cityRef).toList()
        refs shouldHaveSize 1
    }

    // ======================================================================
    // ProjectionRepository: countBy/existsBy with Ref
    // ======================================================================

    @Test
    fun `countBy with field and ref value should count matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        repo.countBy(cityPath, cityRef) shouldBe 4
    }

    @Test
    fun `existsBy with field and ref value should return true when match exists`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        repo.existsBy(cityPath, cityRef) shouldBe true
    }

    @Test
    fun `existsBy with field and ref value should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 999)
        repo.existsBy(cityPath, cityRef) shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: Slice methods
    // ======================================================================

    @Test
    fun `slice should return first page of projections`() {
        val repo = orm.projection(OwnerView::class)
        val idPath = metamodel<OwnerView, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `slice with large size should not have next`() {
        val repo = orm.projection(OwnerView::class)
        val idPath = metamodel<OwnerView, Int>(repo.model, "id")
        val slice = repo.select().orderBy(idPath).slice(100)
        slice.content shouldHaveSize 10
        slice.hasNext shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: findAllBy with Ref iterable
    // ======================================================================

    @Test
    fun `findAllBy with field and iterable of ref values should return matching`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(
            Ref.of(City::class.java, 1),
            Ref.of(City::class.java, 3),
            Ref.of(City::class.java, 5),
        )
        // We use findAllByRef with metamodel
        val views = repo.findAllByRef(cityPath, refs)
        // City 1: Betty, City 3: Eduardo, City 5: Jean + Jeff = 4 total
        views shouldHaveSize 4
    }

    @Test
    fun `selectBy with field and iterable of ref values should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(
            Ref.of(City::class.java, 1),
            Ref.of(City::class.java, 3),
        )
        val count = repo.selectByRef(cityPath, refs).count()
        count shouldBe 2
    }

    // ======================================================================
    // RepositoryLookup: projection-level reified extensions
    // ======================================================================

    @Test
    fun `orm findBy reified projection with ref should return matching`() {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val view = orm.findBy<OwnerView, City>(cityPath, cityRef)
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `orm getBy reified projection with ref should return matching`() {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val view = orm.getBy<OwnerView, City>(cityPath, cityRef)
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `orm findAllBy reified projection with ref should return matching`() {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val views = orm.findAllBy<OwnerView, City>(cityPath, cityRef)
        views shouldHaveSize 4
    }

    @Test
    fun `orm countBy reified projection with ref should count matching`() {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val count = orm.countBy<OwnerView, City>(cityPath, cityRef)
        count shouldBe 4
    }

    @Test
    fun `orm existsBy reified projection with ref should return true`() {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        orm.existsBy<OwnerView, City>(cityPath, cityRef) shouldBe true
    }

    @Test
    fun `orm selectBy reified projection with ref should return matching flow`(): Unit = runBlocking {
        val cityPath = metamodel<OwnerView, City>(orm.projection(OwnerView::class).model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val count = orm.selectBy<OwnerView, City>(cityPath, cityRef).count()
        count shouldBe 4
    }

    @Test
    fun `orm findRefBy reified projection should return ref`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val ref = orm.findRefBy<OwnerView, String>(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `orm getRefBy reified projection should return ref`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val ref = orm.getRefBy<OwnerView, String>(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `orm selectRefBy reified projection should return flow of refs`(): Unit = runBlocking {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val count = orm.selectRefBy<OwnerView, String>(firstNamePath, "Betty").count()
        count shouldBe 1
    }

    @Test
    fun `orm findAllRefBy reified projection should return refs`() {
        val lastNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "last_name")
        val refs = orm.findAllRefBy<OwnerView, String>(lastNamePath, "Davis")
        refs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: findBy/getBy with non-Ref value
    // ======================================================================

    @Test
    fun `findBy with field and string value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.findBy(firstNamePath, "Betty")
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findBy with field and string value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.findBy(firstNamePath, "NonExistentName")
        view.shouldBeNull()
    }

    @Test
    fun `getBy with field and string value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.getBy(firstNamePath, "Betty")
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getBy with field and string value should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.getBy(firstNamePath, "NonExistentName")
        }
    }

    // ======================================================================
    // ProjectionRepository: findAllBy with non-Ref value and iterable
    // ======================================================================

    @Test
    fun `findAllBy with field and string value should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val views = repo.findAllBy(lastNamePath, "Davis")
        views shouldHaveSize 2
    }

    @Test
    fun `findAllBy with field and iterable of values should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.findAllBy(firstNamePath, listOf("Betty", "Eduardo"))
        views shouldHaveSize 2
    }

    @Test
    fun `selectBy with field and string value should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val count = repo.selectBy(lastNamePath, "Davis").count()
        count shouldBe 2
    }

    @Test
    fun `selectBy with field and iterable of values should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.selectBy(firstNamePath, listOf("Betty", "Eduardo")).count()
        count shouldBe 2
    }

    // ======================================================================
    // ProjectionRepository: findRefBy/getRefBy with non-Ref value
    // ======================================================================

    @Test
    fun `findRefBy with field and string value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRefBy<Any, Any, String>(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `findRefBy with field and string value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRefBy<Any, Any, String>(firstNamePath, "NonExistentName")
        ref.shouldBeNull()
    }

    @Test
    fun `getRefBy with field and string value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.getRefBy(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRefBy with field and string value should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.getRefBy(firstNamePath, "NonExistentName")
        }
    }

    @Test
    fun `getRefBy with field and ref value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 1)
        val ref = repo.getRefBy(cityPath, cityRef)
        ref.shouldNotBeNull()
    }

    // ======================================================================
    // ProjectionRepository: findAllRefBy variants
    // ======================================================================

    @Test
    fun `findAllRefBy with field and string value should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.findAllRefBy(lastNamePath, "Davis")
        refs shouldHaveSize 2
    }

    @Test
    fun `findAllRefBy with field and ref value should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val refs = repo.findAllRefBy(cityPath, cityRef)
        refs shouldHaveSize 4
    }

    @Test
    fun `findAllRefByRef with metamodel and iterable of refs should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(Ref.of(City::class.java, 1), Ref.of(City::class.java, 3))
        val resultRefs = repo.findAllRefByRef(cityPath, refs)
        resultRefs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: selectRefBy variants
    // ======================================================================

    @Test
    fun `selectRefBy with field and string value should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.selectRefBy(lastNamePath, "Davis").toList()
        refs shouldHaveSize 2
    }

    @Test
    fun `selectRefBy with field and ref value for multiple results should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cityRef: Ref<City> = Ref.of(City::class.java, 2)
        val refs = repo.selectRefBy(cityPath, cityRef).toList()
        refs shouldHaveSize 4
    }

    @Test
    fun `selectRefBy with field and iterable of values should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val refs = repo.selectRefBy(firstNamePath, listOf("Betty", "Eduardo")).toList()
        refs shouldHaveSize 2
    }

    @Test
    fun `selectRefByRef with metamodel and iterable of refs should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val refs = listOf(Ref.of(City::class.java, 1), Ref.of(City::class.java, 3))
        val resultRefs = repo.selectRefByRef(cityPath, refs).toList()
        resultRefs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: countBy/existsBy with non-Ref value
    // ======================================================================

    @Test
    fun `countBy with field and string value should count matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        repo.countBy(lastNamePath, "Davis") shouldBe 2
    }

    @Test
    fun `existsBy with field and string value should return true when match exists`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.existsBy(firstNamePath, "Betty") shouldBe true
    }

    @Test
    fun `existsBy with field and string value should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.existsBy(firstNamePath, "NonExistentName") shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: find/get/findRef/getRef with predicate
    // ======================================================================

    @Test
    fun `find with WhereBuilder predicate should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.find { where(firstNamePath, EQUALS, "Betty") }
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `find with WhereBuilder predicate should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.find { where(firstNamePath, EQUALS, "NonExistentName") }
        view.shouldBeNull()
    }

    @Test
    fun `get with WhereBuilder predicate should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.get { where(firstNamePath, EQUALS, "Betty") }
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findRef with WhereBuilder predicate should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRef { where(firstNamePath, EQUALS, "Betty") }
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRef with WhereBuilder predicate should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.getRef { where(firstNamePath, EQUALS, "Betty") }
        ref.shouldNotBeNull()
    }

    // ======================================================================
    // ProjectionRepository: findAll/findAllRef with predicate
    // ======================================================================

    @Test
    fun `findAll with WhereBuilder predicate should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val views = repo.findAll { where(lastNamePath, EQUALS, "Davis") }
        views shouldHaveSize 2
    }

    @Test
    fun `findAllRef with WhereBuilder predicate should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.findAllRef { where(lastNamePath, EQUALS, "Davis") }
        refs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: select/selectRef with predicate
    // ======================================================================

    @Test
    fun `select with WhereBuilder predicate should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val count = repo.select { where(lastNamePath, EQUALS, "Davis") }.count()
        count shouldBe 2
    }

    @Test
    fun `selectRef with WhereBuilder predicate should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.selectRef { where(lastNamePath, EQUALS, "Davis") }.toList()
        refs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: count/exists with predicate
    // ======================================================================

    @Test
    fun `count with WhereBuilder predicate should count matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val count = repo.count { where(lastNamePath, EQUALS, "Davis") }
        count shouldBe 2
    }

    @Test
    fun `exists with WhereBuilder predicate should return true when match exists`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.exists { where(firstNamePath, EQUALS, "Betty") } shouldBe true
    }

    @Test
    fun `exists with WhereBuilder predicate should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.exists { where(firstNamePath, EQUALS, "NonExistent") } shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: findAllRef/selectAllRef
    // ======================================================================

    @Test
    fun `findAllRef should return all projection refs`() {
        val repo = orm.projection(OwnerView::class)
        val refs = repo.findAllRef()
        refs shouldHaveSize 10
    }

    @Test
    fun `selectAllRef should return all projection refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.selectAllRef().count()
        count shouldBe 10
    }

    // ======================================================================
    // ProjectionRepository: findAllRefBy with iterable of Data values
    // ======================================================================

    @Test
    fun `findAllRefBy with field and iterable of Data values should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cities = listOf(City(id = 1, name = "Sun Prairie"), City(id = 3, name = "McFarland"))
        val refs = repo.findAllRefBy(cityPath, cities)
        refs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: basic count/exists
    // ======================================================================

    @Test
    fun `count should return total number of projections`() {
        val repo = orm.projection(OwnerView::class)
        repo.count() shouldBe 10
    }

    @Test
    fun `exists should return true when projections exist`() {
        val repo = orm.projection(OwnerView::class)
        repo.exists() shouldBe true
    }

    @Test
    fun `existsById should return true for existing id`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsById(1) shouldBe true
    }

    @Test
    fun `existsById should return false for non-existing id`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsById(999) shouldBe false
    }

    @Test
    fun `existsByRef should return true for existing ref`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsByRef(Ref.of(OwnerView::class.java, 1)) shouldBe true
    }

    @Test
    fun `existsByRef should return false for non-existing ref`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsByRef(Ref.of(OwnerView::class.java, 999)) shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: findById/findByRef/getById/getByRef
    // ======================================================================

    @Test
    fun `findById should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val view = repo.findById(1)
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findById should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val view = repo.findById(999)
        view.shouldBeNull()
    }

    @Test
    fun `findByRef should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val view = repo.findByRef(Ref.of(OwnerView::class.java, 1))
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getById should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val view = repo.getById(1)
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getByRef should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val view = repo.getByRef(Ref.of(OwnerView::class.java, 1))
        view.firstName shouldBe "Betty"
    }

    // ======================================================================
    // ProjectionRepository: findAll/findAllById/findAllByRef
    // ======================================================================

    @Test
    fun `findAll should return all projections`() {
        val repo = orm.projection(OwnerView::class)
        val views = repo.findAll()
        views shouldHaveSize 10
    }

    @Test
    fun `findAllById should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val views = repo.findAllById(listOf(1, 2, 3))
        views shouldHaveSize 3
    }

    @Test
    fun `findAllByRef should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val refs = listOf(
            Ref.of(OwnerView::class.java, 1),
            Ref.of(OwnerView::class.java, 2),
        )
        val views = repo.findAllByRef(refs)
        views shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: selectAll/selectById/selectByRef (flow-based)
    // ======================================================================

    @Test
    fun `selectAll should return all projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.selectAll().count()
        count shouldBe 10
    }

    @Test
    fun `selectById should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.selectById(kotlinx.coroutines.flow.flowOf(1, 2, 3)).count()
        count shouldBe 3
    }

    @Test
    fun `selectByRef should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = kotlinx.coroutines.flow.flowOf(
            Ref.of(OwnerView::class.java, 1),
            Ref.of(OwnerView::class.java, 2),
        )
        val count = repo.selectByRef(refs).count()
        count shouldBe 2
    }

    @Test
    fun `selectById with chunkSize should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.selectById(kotlinx.coroutines.flow.flowOf(1, 2, 3), 2).count()
        count shouldBe 3
    }

    @Test
    fun `selectByRef with chunkSize should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = kotlinx.coroutines.flow.flowOf(
            Ref.of(OwnerView::class.java, 1),
            Ref.of(OwnerView::class.java, 2),
        )
        val count = repo.selectByRef(refs, 2).count()
        count shouldBe 2
    }

    // ======================================================================
    // ProjectionRepository: countById/countByRef (flow-based suspend)
    // ======================================================================

    @Test
    fun `countById should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.countById(kotlinx.coroutines.flow.flowOf(1, 2, 3))
        count shouldBe 3
    }

    @Test
    fun `countById with chunkSize should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.countById(kotlinx.coroutines.flow.flowOf(1, 2, 3), 2)
        count shouldBe 3
    }

    @Test
    fun `countByRef should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = kotlinx.coroutines.flow.flowOf(
            Ref.of(OwnerView::class.java, 1),
            Ref.of(OwnerView::class.java, 2),
        )
        val count = repo.countByRef(refs)
        count shouldBe 2
    }

    @Test
    fun `countByRef with chunkSize should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = kotlinx.coroutines.flow.flowOf(
            Ref.of(OwnerView::class.java, 1),
            Ref.of(OwnerView::class.java, 2),
        )
        val count = repo.countByRef(refs, 2)
        count shouldBe 2
    }

    // ======================================================================
    // ProjectionRepository: PredicateBuilder-direct variants (no WhereBuilder lambda)
    // ======================================================================

    @Test
    fun `find with direct PredicateBuilder should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "Betty"
        val view = repo.find(predicate)
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `find with direct PredicateBuilder should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "NonExistent"
        val view = repo.find(predicate)
        view.shouldBeNull()
    }

    @Test
    fun `get with direct PredicateBuilder should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "Betty"
        val view = repo.get(predicate)
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `get with direct PredicateBuilder should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "NonExistent"
        assertThrows<NoResultException> {
            repo.get(predicate)
        }
    }

    @Test
    fun `findRef with direct PredicateBuilder should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "Betty"
        val ref = repo.findRef(predicate)
        ref.shouldNotBeNull()
    }

    @Test
    fun `findRef with direct PredicateBuilder should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "NonExistent"
        val ref = repo.findRef(predicate)
        ref.shouldBeNull()
    }

    @Test
    fun `getRef with direct PredicateBuilder should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "Betty"
        val ref = repo.getRef(predicate)
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRef with direct PredicateBuilder should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "NonExistent"
        assertThrows<NoResultException> {
            repo.getRef(predicate)
        }
    }

    @Test
    fun `findAll with direct PredicateBuilder should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val views = repo.findAll(predicate)
        views shouldHaveSize 2
    }

    @Test
    fun `findAllRef with direct PredicateBuilder should return matching refs`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val refs = repo.findAllRef(predicate)
        refs shouldHaveSize 2
    }

    @Test
    fun `select with direct PredicateBuilder should return matching flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val count = repo.select(predicate).count()
        count shouldBe 2
    }

    @Test
    fun `selectRef with direct PredicateBuilder should return matching refs flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val refs = repo.selectRef(predicate).toList()
        refs shouldHaveSize 2
    }

    @Test
    fun `count with direct PredicateBuilder should count matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        repo.count(predicate) shouldBe 2
    }

    @Test
    fun `exists with direct PredicateBuilder should return true when match exists`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "Betty"
        repo.exists(predicate) shouldBe true
    }

    @Test
    fun `exists with direct PredicateBuilder should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val predicate = firstNamePath eq "NonExistent"
        repo.exists(predicate) shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: Metamodel.Key-based findBy/getBy
    // ======================================================================

    @Test
    fun `findBy with Metamodel Key should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val view = repo.findBy(idKey, 1)
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findBy with Metamodel Key should return null for non-existing key`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val view = repo.findBy(idKey, 999)
        view.shouldBeNull()
    }

    @Test
    fun `getBy with Metamodel Key should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val view = repo.getBy(idKey, 1)
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getBy with Metamodel Key should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        assertThrows<NoResultException> {
            repo.getBy(idKey, 999)
        }
    }

    @Test
    fun `findByRef with Metamodel Key should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val view = repo.findByRef(cityKey, Ref.of(City::class.java, 1))
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findByRef with Metamodel Key should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val view = repo.findByRef(cityKey, Ref.of(City::class.java, 999))
        view.shouldBeNull()
    }

    @Test
    fun `getByRef with Metamodel Key should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        val view = repo.getByRef(cityKey, Ref.of(City::class.java, 1))
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getByRef with Metamodel Key should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val cityKey = metamodel<OwnerView, City>(repo.model, "city_id").key()
        assertThrows<NoResultException> {
            repo.getByRef(cityKey, Ref.of(City::class.java, 999))
        }
    }

    // ======================================================================
    // ProjectionRepository: Slice methods with Metamodel.Key
    // ======================================================================

    @Test
    fun `slice with Metamodel Key should return first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.slice(idKey, 3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `slice with Metamodel Key should return all when size exceeds total`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.slice(idKey, 100)
        slice.content shouldHaveSize 10
        slice.hasNext shouldBe false
    }

    @Test
    fun `sliceBefore with Metamodel Key should return descending first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBefore(idKey, 3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
        // First item should be the highest id
        slice.content[0].id shouldBe 10
    }

    @Test
    fun `sliceRef with Metamodel Key should return first page of refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceRef(idKey, 3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `sliceBeforeRef with Metamodel Key should return descending first page of refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBeforeRef(idKey, 3)
        slice.content shouldHaveSize 3
        slice.hasNext shouldBe true
    }

    @Test
    fun `slice with Metamodel Key and WhereBuilder predicate should filter results`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.slice(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `slice with Metamodel Key and direct PredicateBuilder should filter results`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val slice = repo.slice(idKey, 10, predicate)
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `sliceRef with Metamodel Key and WhereBuilder predicate should filter results`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceRef(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `sliceRef with Metamodel Key and direct PredicateBuilder should filter results`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val slice = repo.sliceRef(idKey, 10, predicate)
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `sliceBefore with Metamodel Key and WhereBuilder should filter results descending`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `sliceBefore with Metamodel Key and direct PredicateBuilder should filter results descending`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val predicate = lastNamePath eq "Davis"
        val slice = repo.sliceBefore(idKey, 10, predicate)
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    // ======================================================================
    // ProjectionRepository: Slice cursor-based navigation (sliceAfter/sliceBefore with cursor)
    // ======================================================================

    @Test
    fun `sliceAfter with cursor should return next page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val firstSlice = repo.slice(idKey, 3)
        firstSlice.content shouldHaveSize 3
        // Get the last ID from the first slice to use as cursor
        val lastId = firstSlice.content.last().id
        val nextSlice = repo.select().sliceAfter(idKey, lastId, 3)
        nextSlice.content shouldHaveSize 3
        // All IDs in next slice should be greater than lastId
        nextSlice.content.forEach { it.id shouldBe (it.id).also { id -> assert(id > lastId) } }
    }

    @Test
    fun `sliceBefore with cursor should return previous page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        // Get the last page first
        val lastSlice = repo.sliceBefore(idKey, 3)
        lastSlice.content shouldHaveSize 3
        // Navigate backward
        val firstId = lastSlice.content.last().id
        val previousSlice = repo.select().sliceBefore(idKey, firstId, 3)
        previousSlice.content shouldHaveSize 3
    }

    // ======================================================================
    // ProjectionRepository: selectAllRef flow
    // ======================================================================

    @Test
    fun `selectAllRef flow should return all refs`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = repo.selectAllRef().toList()
        refs shouldHaveSize 10
        refs.forEach { it.shouldNotBeNull() }
    }

    // ======================================================================
    // ProjectionRepository: findAllBy with Iterable<String> (non-Ref iterable)
    // ======================================================================

    @Test
    fun `selectRef with IN operator and string values should return refs`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val refs = repo.selectRef().where(firstNamePath, IN, listOf("Betty", "Eduardo")).resultList
        refs shouldHaveSize 2
    }

    @Test
    fun `selectRefBy with field and iterable of Data values should return refs flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val cities = listOf(City(id = 1, name = "Sun Prairie"), City(id = 3, name = "McFarland"))
        val refs = repo.selectRefByRef(cityPath, cities.map { Ref.of(City::class.java, it.id) }).toList()
        refs shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: getRefBy with Ref value
    // ======================================================================

    @Test
    fun `getRefBy with ref value should throw for non-existing ref`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        assertThrows<NoResultException> {
            repo.getRefBy(cityPath, Ref.of(City::class.java, 999))
        }
    }

    // ======================================================================
    // ProjectionRepository: findRefBy with Ref value
    // ======================================================================

    @Test
    fun `findRefBy with ref value should return null for non-existing ref`() {
        val repo = orm.projection(OwnerView::class)
        val cityPath = metamodel<OwnerView, City>(repo.model, "city_id")
        val ref = repo.findRefBy(cityPath, Ref.of(City::class.java, 999))
        ref.shouldBeNull()
    }

    // ======================================================================
    // Projection QueryBuilder: slice on QueryBuilder level
    // ======================================================================

    @Test
    fun `projection query builder slice should return first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.select().slice(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `projection query builder sliceBefore should return descending first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.select().sliceBefore(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `projection query builder sliceRef should return refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.selectRef().slice(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    // ======================================================================
    // ProjectionRepository: slice default methods
    // ======================================================================

    @Test
    fun `repo slice with key and size should return first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.slice(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceBefore with key and size should return last page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBefore(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceRef with key and size should return first page of refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceRef(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceBeforeRef with key and size should return refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBeforeRef(idKey, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo slice with key and predicate should return filtered first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.slice(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `repo sliceRef with key and predicate should return filtered refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceRef(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `repo sliceBefore with key and predicate should return filtered last page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `repo sliceBeforeRef with key and predicate should return filtered refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
        slice.hasNext shouldBe false
    }

    @Test
    fun `repo sliceAfter with key after value and size should return next page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceAfter(idKey, 3, 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceAfterRef with key after value and size should return next page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceAfterRef(idKey, 3, 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceBefore with key before value and size should return previous page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBefore(idKey, 8, 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceBeforeRef with key before value and size should return previous page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val slice = repo.sliceBeforeRef(idKey, 8, 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceAfter with key after value predicate and size should return filtered next page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfter(idKey, 1, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 1
    }

    @Test
    fun `repo sliceAfterRef with key after value predicate and size should return filtered next page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfterRef(idKey, 1, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 1
    }

    @Test
    fun `repo sliceBefore with key before value predicate and size should return filtered previous page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, 10, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
    }

    @Test
    fun `repo sliceBeforeRef with key before value predicate and size should return filtered previous page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 10, 10) { where(lastNamePath, EQUALS, "Davis") }
        slice.content shouldHaveSize 2
    }

    // ======================================================================
    // ProjectionRepository: slice with sort metamodel
    // ======================================================================

    @Test
    fun `repo slice with key sort and size should return sorted first page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.slice(idKey, lastNamePath, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceBefore with key sort and size should return sorted last page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, lastNamePath, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceRef with key sort and size should return sorted first page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceRef(idKey, lastNamePath, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceBeforeRef with key sort and size should return sorted refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, lastNamePath, 4)
        slice.content shouldHaveSize 4
        slice.hasNext shouldBe true
    }

    @Test
    fun `repo sliceAfter with key sort and cursors should return next sorted page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfter(idKey, 3, lastNamePath, "Davis", 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceBefore with key sort and cursors should return previous sorted page`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBefore(idKey, 8, lastNamePath, "Smith", 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceAfterRef with key sort and cursors should return next sorted page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceAfterRef(idKey, 3, lastNamePath, "Davis", 4)
        slice.content shouldHaveSize 4
    }

    @Test
    fun `repo sliceBeforeRef with key sort and cursors should return previous sorted page refs`() {
        val repo = orm.projection(OwnerView::class)
        val idKey = metamodel<OwnerView, Int>(repo.model, "id").key()
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val slice = repo.sliceBeforeRef(idKey, 8, lastNamePath, "Smith", 4)
        slice.content shouldHaveSize 4
    }
}
