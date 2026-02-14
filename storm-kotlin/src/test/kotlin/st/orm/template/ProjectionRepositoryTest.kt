package st.orm.template

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.*
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
import st.orm.repository.*
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class ProjectionRepositoryTest(
    @Autowired val orm: ORMTemplate,
) {
    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // --- ProjectionRepository: findAll ---

    @Test
    fun `findAll should return all owner views`() {
        // data.sql inserts exactly 10 owners (ids 1-10). OwnerView is backed by owner_view.
        val repo = orm.projection(OwnerView::class)
        val views = repo.findAll()
        views shouldHaveSize 10
    }

    // --- ProjectionRepository: findById ---

    @Test
    fun `findById should return owner view when exists`() {
        // data.sql: Owner(id=1, first_name='Betty', last_name='Davis', ...).
        val repo = orm.projection(OwnerView::class)
        val view = repo.findById(1)
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findById should return null when not exists`() {
        val repo = orm.projection(OwnerView::class)
        repo.findById(999).shouldBeNull()
    }

    // --- ProjectionRepository: findByRef ---

    @Test
    fun `findByRef should return owner view for valid ref`() {
        val repo = orm.projection(OwnerView::class)
        val ref = repo.ref(1)
        val view = repo.findByRef(ref)
        view.shouldNotBeNull()
        view.id shouldBe 1
    }

    @Test
    fun `findByRef should return null for non-existent ref`() {
        val repo = orm.projection(OwnerView::class)
        val ref = repo.ref(999)
        repo.findByRef(ref).shouldBeNull()
    }

    // --- ProjectionRepository: getById ---

    @Test
    fun `getById should return owner view when exists`() {
        // data.sql: Owner(id=2, first_name='George', last_name='Franklin', ...).
        val repo = orm.projection(OwnerView::class)
        val view = repo.getById(2)
        view.firstName shouldBe "George"
    }

    @Test
    fun `getById should throw NoResultException when not exists`() {
        val repo = orm.projection(OwnerView::class)
        assertThrows<NoResultException> {
            repo.getById(999)
        }
    }

    // --- ProjectionRepository: getByRef ---

    @Test
    fun `getByRef should return owner view when exists`() {
        // data.sql: Owner(id=3, first_name='Eduardo', last_name='Rodriquez', ...).
        val repo = orm.projection(OwnerView::class)
        val ref = repo.ref(3)
        val view = repo.getByRef(ref)
        view.firstName shouldBe "Eduardo"
    }

    @Test
    fun `getByRef should throw NoResultException when not exists`() {
        val repo = orm.projection(OwnerView::class)
        val ref = repo.ref(999)
        assertThrows<NoResultException> {
            repo.getByRef(ref)
        }
    }

    // --- ProjectionRepository: count and exists ---

    @Test
    fun `count should return total number of owner views`() {
        // data.sql inserts 10 owners, so owner_view should also have 10 rows.
        val repo = orm.projection(OwnerView::class)
        repo.count() shouldBe 10
    }

    @Test
    fun `exists should return true when projections exist`() {
        val repo = orm.projection(OwnerView::class)
        repo.exists() shouldBe true
    }

    @Test
    fun `existsById should return true for existing owner view`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsById(1) shouldBe true
    }

    @Test
    fun `existsById should return false for non-existent owner view`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsById(999) shouldBe false
    }

    @Test
    fun `existsByRef should return true for existing owner view ref`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsByRef(repo.ref(1)) shouldBe true
    }

    @Test
    fun `existsByRef should return false for non-existent owner view ref`() {
        val repo = orm.projection(OwnerView::class)
        repo.existsByRef(repo.ref(999)) shouldBe false
    }

    // --- ProjectionRepository: findAllById and findAllByRef ---

    @Test
    fun `findAllById should return matching owner views`() {
        val repo = orm.projection(OwnerView::class)
        val views = repo.findAllById(listOf(1, 2, 3))
        views shouldHaveSize 3
    }

    @Test
    fun `findAllById with non-existent ids should return only existing`() {
        val repo = orm.projection(OwnerView::class)
        val views = repo.findAllById(listOf(1, 999))
        views shouldHaveSize 1
        views.first().id shouldBe 1
    }

    @Test
    fun `findAllByRef should return matching owner views`() {
        val repo = orm.projection(OwnerView::class)
        val refs = listOf(repo.ref(1), repo.ref(2))
        val views = repo.findAllByRef(refs)
        views shouldHaveSize 2
    }

    // --- ProjectionRepository: ref operations ---

    @Test
    fun `ref from id should create ref with correct id`() {
        val repo = orm.projection(OwnerView::class)
        val ref = repo.ref(1)
        ref.shouldNotBeNull()
    }

    @Test
    fun `findAllRef should return all refs`() {
        val repo = orm.projection(OwnerView::class)
        val refs = repo.findAllRef()
        refs shouldHaveSize 10
    }

    @Test
    fun `selectAllRef should return flow of all refs`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = repo.selectAllRef().toList()
        refs shouldHaveSize 10
    }

    // --- ProjectionRepository: Flow operations ---

    @Test
    fun `selectAll should return flow of all owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        repo.selectAll().count() shouldBe 10
    }

    @Test
    fun `selectById with flow of ids should return matching owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val ids = flowOf(1, 2, 3)
        val views = repo.selectById(ids).toList()
        views shouldHaveSize 3
    }

    @Test
    fun `selectByRef with flow of refs should return matching owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = flowOf(repo.ref(1), repo.ref(2))
        val views = repo.selectByRef(refs).toList()
        views shouldHaveSize 2
    }

    @Test
    fun `selectById with custom chunk size should return matching owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val ids = (1..10).asFlow()
        val views = repo.selectById(ids, 3).toList()
        views shouldHaveSize 10
    }

    @Test
    fun `selectByRef with custom chunk size should return matching owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = (1..10).map { repo.ref(it) }.asFlow()
        val views = repo.selectByRef(refs, 3).toList()
        views shouldHaveSize 10
    }

    @Test
    fun `selectById with empty flow should return empty flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val views = repo.selectById(emptyFlow()).toList()
        views.shouldBeEmpty()
    }

    @Test
    fun `selectByRef with empty flow should return empty flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val views = repo.selectByRef(emptyFlow()).toList()
        views.shouldBeEmpty()
    }

    // --- ProjectionRepository: countById and countByRef ---

    @Test
    fun `countById should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.countById(flowOf(1, 2, 3))
        count shouldBe 3
    }

    @Test
    fun `countById with empty flow should return zero`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.countById(emptyFlow())
        count shouldBe 0
    }

    @Test
    fun `countByRef should count matching projections from flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val refs = flowOf(repo.ref(1), repo.ref(2))
        val count = repo.countByRef(refs)
        count shouldBe 2
    }

    @Test
    fun `countByRef with empty flow should return zero`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val count = repo.countByRef(emptyFlow())
        count shouldBe 0
    }

    // --- ProjectionRepository: predicate-based methods ---

    @Test
    fun `findAll with predicate should filter owner views`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.findAll { where(firstNamePath, EQUALS, "Betty") }
        views shouldHaveSize 1
        views.first().firstName shouldBe "Betty"
    }

    @Test
    fun `findAll with predicate returning empty should return empty list`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.findAll { where(firstNamePath, EQUALS, "NonExistent") }
        views.shouldBeEmpty()
    }

    @Test
    fun `find with predicate should return matching owner view`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.find { where(firstNamePath, EQUALS, "George") }
        view.shouldNotBeNull()
        view.firstName shouldBe "George"
    }

    @Test
    fun `find with predicate should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.find { where(firstNamePath, EQUALS, "NonExistent") }
        view.shouldBeNull()
    }

    @Test
    fun `get with predicate should return matching owner view`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.get { where(firstNamePath, EQUALS, "Eduardo") }
        view.firstName shouldBe "Eduardo"
    }

    @Test
    fun `get with predicate should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.get { where(firstNamePath, EQUALS, "NonExistent") }
        }
    }

    @Test
    fun `count with predicate should count matching owner views`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.count { where(firstNamePath, EQUALS, "Betty") }
        count shouldBe 1
    }

    @Test
    fun `exists with predicate should return true for matching owner view`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.exists { where(firstNamePath, EQUALS, "Betty") } shouldBe true
    }

    @Test
    fun `exists with predicate should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.exists { where(firstNamePath, EQUALS, "NonExistent") } shouldBe false
    }

    @Test
    fun `select with predicate should return flow of matching owner views`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.select { where(firstNamePath, EQUALS, "Betty") }.count()
        count shouldBe 1
    }

    @Test
    fun `findAllRef with predicate should return refs of matching owner views`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val refs = repo.findAllRef { where(firstNamePath, EQUALS, "Betty") }
        refs shouldHaveSize 1
    }

    @Test
    fun `findRef with predicate should return ref for matching owner view`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRef { where(firstNamePath, EQUALS, "George") }
        ref.shouldNotBeNull()
    }

    @Test
    fun `findRef with predicate should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRef { where(firstNamePath, EQUALS, "NonExistent") }
        ref.shouldBeNull()
    }

    @Test
    fun `getRef with predicate should return ref for matching owner view`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.getRef { where(firstNamePath, EQUALS, "Eduardo") }
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRef with predicate should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.getRef { where(firstNamePath, EQUALS, "NonExistent") }
        }
    }

    @Test
    fun `selectRef with predicate should return flow of refs`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.selectRef { where(firstNamePath, EQUALS, "Betty") }.count()
        count shouldBe 1
    }

    // --- RepositoryLookup extension methods for projections ---

    @Test
    fun `orm findAll reified should return all owner views`() {
        val views = orm.findAll<OwnerView>()
        views shouldHaveSize 10
    }

    @Test
    fun `orm selectAll reified should return all owner views as flow`(): Unit = runBlocking {
        orm.selectAll<OwnerView>().count() shouldBe 10
    }

    @Test
    fun `orm findAllRef reified should return all owner view refs`() {
        val refs = orm.findAllRef<OwnerView>()
        refs shouldHaveSize 10
    }

    @Test
    fun `orm selectAllRef reified should return all owner view refs as flow`(): Unit = runBlocking {
        orm.selectAllRef<OwnerView>().count() shouldBe 10
    }

    @Test
    fun `orm countAll reified should return total count of owner views`() {
        orm.countAll<OwnerView>() shouldBe 10
    }

    @Test
    fun `orm exists reified should return true when owner views exist`() {
        orm.exists<OwnerView>() shouldBe true
    }

    // --- RepositoryLookup predicate extension methods for projections ---

    @Test
    fun `orm findAll with predicate should filter owner views`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val views = orm.findAll<OwnerView> { where(firstNamePath, EQUALS, "Betty") }
        views shouldHaveSize 1
        views.first().firstName shouldBe "Betty"
    }

    @Test
    fun `orm find with predicate should return single matching owner view`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val view = orm.find<OwnerView> { where(firstNamePath, EQUALS, "George") }
        view.shouldNotBeNull()
        view.firstName shouldBe "George"
    }

    @Test
    fun `orm find with predicate should return null when no match`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val view = orm.find<OwnerView> { where(firstNamePath, EQUALS, "Nonexistent") }
        view.shouldBeNull()
    }

    @Test
    fun `orm get with predicate should return matching owner view`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val view = orm.get<OwnerView> { where(firstNamePath, EQUALS, "Eduardo") }
        view.firstName shouldBe "Eduardo"
    }

    @Test
    fun `orm get with predicate should throw when no match`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        assertThrows<NoResultException> {
            orm.get<OwnerView> { where(firstNamePath, EQUALS, "Nonexistent") }
        }
    }

    @Test
    fun `orm count with predicate should count matching owner views`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val count = orm.count<OwnerView> { where(firstNamePath, EQUALS, "Betty") }
        count shouldBe 1
    }

    @Test
    fun `orm exists with predicate should return true for match`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        orm.exists<OwnerView> { where(firstNamePath, EQUALS, "Betty") } shouldBe true
    }

    @Test
    fun `orm exists with predicate should return false for no match`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        orm.exists<OwnerView> { where(firstNamePath, EQUALS, "Nonexistent") } shouldBe false
    }

    @Test
    fun `orm select with predicate should return matching flow`(): Unit = runBlocking {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val count = orm.select<OwnerView> { where(firstNamePath, EQUALS, "Betty") }.count()
        count shouldBe 1
    }

    @Test
    fun `orm findAllRef with predicate should return refs of matching owner views`() {
        val firstNamePath = metamodel<OwnerView, String>(orm.projection(OwnerView::class).model, "first_name")
        val refs = orm.findAllRef<OwnerView> { where(firstNamePath, EQUALS, "Betty") }
        refs shouldHaveSize 1
    }

    // --- ProjectionRepository: findBy/getBy with Metamodel ---

    @Test
    fun `findBy with field and value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.findBy(firstNamePath, "Betty")
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findBy with field and value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.findBy(firstNamePath, "NonExistent")
        view.shouldBeNull()
    }

    @Test
    fun `findAllBy with field and single value should return matching projections`() {
        // data.sql: Two owners have last_name 'Davis': Betty Davis (id=1) and Harold Davis (id=4).
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val views = repo.findAllBy(lastNamePath, "Davis")
        views shouldHaveSize 2
    }

    @Test
    fun `findAllBy with field and iterable values should return matching projections`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.findAllBy(firstNamePath, listOf("Betty", "George", "Eduardo"))
        views shouldHaveSize 3
    }

    @Test
    fun `getBy with field and value should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.getBy(firstNamePath, "Betty")
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `getBy with field and value should throw NoResultException when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.getBy(firstNamePath, "NonExistent")
        }
    }

    // --- ProjectionRepository: countBy/existsBy with Metamodel ---

    @Test
    fun `countBy with field and value should count matching projections`() {
        // data.sql: Two owners have last_name 'Davis': Betty Davis (id=1) and Harold Davis (id=4).
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        repo.countBy(lastNamePath, "Davis") shouldBe 2
    }

    @Test
    fun `existsBy with field and value should return true when match exists`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.existsBy(firstNamePath, "Betty") shouldBe true
    }

    @Test
    fun `existsBy with field and value should return false when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.existsBy(firstNamePath, "NonExistent") shouldBe false
    }

    // --- ProjectionRepository: selectBy with Metamodel ---

    @Test
    fun `selectBy with field and value should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.selectBy(firstNamePath, "Betty").toList()
        views shouldHaveSize 1
        views.first().firstName shouldBe "Betty"
    }

    @Test
    fun `selectBy with field and iterable values should return matching projections as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.selectBy(firstNamePath, listOf("Betty", "George")).toList()
        views shouldHaveSize 2
    }

    // --- ProjectionRepository: findRefBy/getRefBy with Metamodel ---

    @Test
    fun `findRefBy with field and value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRefBy<Any, Any, String>(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `findRefBy with field and value should return null when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRefBy<Any, Any, String>(firstNamePath, "NonExistent")
        ref.shouldBeNull()
    }

    @Test
    fun `findAllRefBy with field and value should return refs of matching projections`() {
        // data.sql: Two owners have last_name 'Davis': Betty Davis (id=1) and Harold Davis (id=4).
        val repo = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(repo.model, "last_name")
        val refs = repo.findAllRefBy(lastNamePath, "Davis")
        refs shouldHaveSize 2
    }

    @Test
    fun `getRefBy with field and value should return matching ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.getRefBy(firstNamePath, "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRefBy with field and value should throw when no match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        assertThrows<NoResultException> {
            repo.getRefBy(firstNamePath, "NonExistent")
        }
    }

    @Test
    fun `selectRefBy with field and value should return matching refs as flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val refs = repo.selectRefBy(firstNamePath, "Betty").toList()
        refs shouldHaveSize 1
    }

    // --- ProjectionRepository: PredicateBuilder direct-call variants ---

    @Test
    fun `findAll with direct PredicateBuilder should filter projections`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val views = repo.findAll(firstNamePath eq "Betty")
        views shouldHaveSize 1
        views.first().firstName shouldBe "Betty"
    }

    @Test
    fun `find with direct PredicateBuilder should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.find(firstNamePath eq "Betty")
        view.shouldNotBeNull()
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `get with direct PredicateBuilder should return matching projection`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val view = repo.get(firstNamePath eq "Betty")
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `findAllRef with direct PredicateBuilder should return refs`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val refs = repo.findAllRef(firstNamePath eq "Betty")
        refs shouldHaveSize 1
    }

    @Test
    fun `findRef with direct PredicateBuilder should return ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.findRef(firstNamePath eq "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `getRef with direct PredicateBuilder should return ref`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val ref = repo.getRef(firstNamePath eq "Betty")
        ref.shouldNotBeNull()
    }

    @Test
    fun `select with direct PredicateBuilder should return flow`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.select(firstNamePath eq "Betty").count()
        count shouldBe 1
    }

    @Test
    fun `selectRef with direct PredicateBuilder should return flow of refs`(): Unit = runBlocking {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        val count = repo.selectRef(firstNamePath eq "Betty").count()
        count shouldBe 1
    }

    @Test
    fun `count with direct PredicateBuilder should count matching`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.count(firstNamePath eq "Betty") shouldBe 1
    }

    @Test
    fun `exists with direct PredicateBuilder should return true for match`() {
        val repo = orm.projection(OwnerView::class)
        val firstNamePath = metamodel<OwnerView, String>(repo.model, "first_name")
        repo.exists(firstNamePath eq "Betty") shouldBe true
    }
}
