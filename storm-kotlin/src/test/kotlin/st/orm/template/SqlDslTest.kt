package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Data
import st.orm.JoinType
import st.orm.Metamodel
import st.orm.Operator.*
import st.orm.Ref
import st.orm.repository.entity
import st.orm.repository.removeAll
import st.orm.repository.select
import st.orm.template.model.City
import st.orm.template.model.Owner
import st.orm.template.model.OwnerView
import st.orm.template.model.Pet
import st.orm.template.model.PetType
import st.orm.template.model.Visit

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
internal open class SqlDslTest(
    @Autowired val orm: ORMTemplate,
) {

    @Suppress("UNCHECKED_CAST")
    private fun <T : Data, V> metamodel(model: Model<*, *>, columnName: String): Metamodel<T, V> = model.columns.first { it.name == columnName }.metamodel as Metamodel<T, V>

    // ORMTemplate: predicate-based select/delete

    @Test
    fun `select with predicate`() {
        val namePath = metamodel<City, String>(orm.model(City::class), "name")
        val cities = orm.select(namePath eq "Madison").resultList
        cities shouldHaveSize 1
        cities[0].name shouldBe "Madison"
    }

    @Test
    fun `select with predicate returns null when no match`() {
        val idPath = metamodel<City, Int>(orm.model(City::class), "id")
        val city = orm.select(idPath eq 999).optionalResult
        city shouldBe null
    }

    @Test
    fun `select with predicate returns single result`() {
        val idPath = metamodel<City, Int>(orm.model(City::class), "id")
        val city = orm.select(idPath eq 2).singleResult
        city.name shouldBe "Madison"
    }

    @Test
    fun `removeAll with predicate`() {
        val idPath = metamodel<Visit, Int>(orm.model(Visit::class), "id")
        val affected = orm.removeAll<Visit>(idPath eq 14)
        affected shouldBe 1
        orm.entity(Visit::class).count() shouldBe 13
    }

    @Test
    fun `removeAll with predicate returns zero when no match`() {
        val idPath = metamodel<City, Int>(orm.model(City::class), "id")
        val affected = orm.removeAll<City>(idPath eq 999)
        affected shouldBe 0
    }

    @Test
    fun `removeAll with greater-than predicate`() {
        val idPath = metamodel<Visit, Int>(orm.model(Visit::class), "id")
        val affected = orm.removeAll<Visit>(idPath greater 13)
        affected shouldBe 1
    }

    // EntityRepository: select { } / delete { } block DSL

    @Test
    fun `select all entities`() {
        val cityRepository = orm.entity(City::class)
        val cities = cityRepository.select().resultList
        cities shouldHaveSize 6
    }

    @Test
    fun `select entity with where predicate`() {
        val cityRepository = orm.entity(City::class)
        val idPath = metamodel<City, Int>(cityRepository.model, "id")
        val city = cityRepository.select { where(idPath eq 1) }.singleResult
        city.id shouldBe 1
    }

    @Test
    fun `select entities with where and orderBy`() {
        val cityRepository = orm.entity(City::class)
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val cities = cityRepository.select {
            where(namePath inList listOf("Madison", "Windsor", "Monona"))
            orderBy(namePath)
        }.resultList
        cities shouldHaveSize 3
        cities[0].name shouldBe "Madison"
        cities[1].name shouldBe "Monona"
        cities[2].name shouldBe "Windsor"
    }

    @Test
    fun `select entities with limit and offset`() {
        val cityRepository = orm.entity(City::class)
        val idPath = metamodel<City, Int>(cityRepository.model, "id")
        val cities = cityRepository.select {
            orderBy(idPath)
            limit(2)
            offset(1)
        }.resultList
        cities shouldHaveSize 2
        cities[0].id shouldBe 2
    }

    @Test
    fun `select entities with reified inner join`() {
        // data.sql: 13 pets total, but Pet(id=13, name='Sly') has NULL owner_id.
        // Inner join excludes pets with no matching owner, yielding 12.
        val pets = orm.entity<Pet>().select {
            innerJoin<Owner, Pet>()
        }.resultList
        pets shouldHaveSize 12
    }

    @Test
    fun `select entities with reified left join`() {
        // data.sql: 13 pets total. Left join preserves all pets, including Pet 13 with NULL owner.
        val pets = orm.entity<Pet>().select {
            leftJoin<Owner, Pet>()
        }.resultList
        pets shouldHaveSize 13
    }

    @Test
    fun `select entities with groupBy and having predicate`() {
        // City has exactly the two columns selected, so both belong in the GROUP BY.
        val cityRepository = orm.entity(City::class)
        val idPath = metamodel<City, Int>(cityRepository.model, "id")
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val cities = cityRepository.select {
            groupBy(idPath, namePath)
            having((namePath eq "Madison") or (namePath eq "Monona"))
            orderBy(namePath)
        }.resultList
        cities shouldHaveSize 2
        cities[0].name shouldBe "Madison"
        cities[1].name shouldBe "Monona"
    }

    @Test
    fun `select entities with groupByAny and havingAny on a joined entity`() {
        // havingAny pairs with groupByAny: a HAVING condition on a joined column is only valid once that column is
        // grouped. data.sql: Betty Davis lives in city 1, Harold Davis in city 4.
        val cityRepository = orm.entity(City::class)
        val idPath = metamodel<City, Int>(cityRepository.model, "id")
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val lastNamePath = metamodel<Owner, String>(orm.model(Owner::class), "last_name")
        val cities = cityRepository.select {
            innerJoin<Owner, City>()
            groupBy(idPath, namePath)
            groupBy(lastNamePath)
            having(lastNamePath eq "Davis")
            orderBy(idPath)
        }.resultList
        cities shouldHaveSize 2
        cities[0].id shouldBe 1
        cities[1].id shouldBe 4
    }

    @Test
    fun `a join inside the block stays queryable after it`() {
        // The block returns the relaxed builder, so the chained continuation can reference the joined entity.
        val lastNamePath = metamodel<Owner, String>(orm.model(Owner::class), "last_name")
        val cities = orm.entity(City::class).select {
            innerJoin<Owner, City>()
        }.where(lastNamePath eq "Davis")
            .resultList
        // data.sql: Betty Davis lives in city 1, Harold Davis in city 4.
        cities shouldHaveSize 2
    }

    @Test
    fun `narrow narrows the block result back to the entity`() {
        val idPath = metamodel<City, Int>(orm.model(City::class), "id")
        val cities = orm.entity(City::class).select {
            innerJoin<Owner, City>()
            where(idPath eq 1)
        }.narrow<City>()
            .resultList
        cities.map { it.id }.toSet() shouldBe setOf(1)
    }

    @Test
    fun `a path on an entity outside the query fails with a descriptive error`() {
        // The block no longer rejects foreign paths at compile time; resolution reports them at query time.
        val visitDatePath = metamodel<Visit, java.time.LocalDate>(orm.model(Visit::class), "visit_date")
        val exception = org.junit.jupiter.api.assertThrows<st.orm.PersistenceException> {
            orm.entity(City::class).select {
                where(visitDatePath eq java.time.LocalDate.now())
            }.resultList
        }
        exception.message!! shouldContain "Visit is not part of this query rooted at City"
    }

    // Clause vocabulary parity with the chained builder (#397).

    @Test
    fun `select entities with whereId in the block`() {
        val cities = orm.entity(City::class).select {
            whereId(listOf(1, 3))
        }.resultList
        cities.map { it.id }.toSet() shouldBe setOf(1, 3)
    }

    @Test
    fun `select entities with whereRef in the block`() {
        val ref1: Ref<City> = Ref.of(City::class.java, 1)
        val ref3: Ref<City> = Ref.of(City::class.java, 3)
        val cities = orm.entity(City::class).select {
            whereRef(listOf(ref1, ref3))
        }.resultList
        cities.map { it.id }.toSet() shouldBe setOf(1, 3)
    }

    @Test
    fun `select entity with forLock template in the block`() {
        val city = orm.entity(City::class).select {
            where(1)
            forLock { "FOR UPDATE" }
        }.singleResult
        city.id shouldBe 1
    }

    @Test
    fun `select entities with aliased class join in the block`() {
        // data.sql: 12 of 13 pets have an owner.
        val pets = orm.entity<Pet>().select {
            join(JoinType.inner(), Owner::class, "o", Pet::class)
        }.resultList
        pets shouldHaveSize 12
    }

    @Test
    fun `select entities with aliased class join and template ON in the block`() {
        val pets = orm.entity<Pet>().select {
            join(JoinType.inner(), Owner::class, "o") { "o.id = ${t(Templates.alias(Pet::class))}.owner_id" }
        }.resultList
        pets shouldHaveSize 12
    }

    @Test
    fun `select entities with class join and template ON in the block`() {
        // City ids are 1-6, pet type ids are 0-5: the matching ids are 1-5.
        val count = orm.entity(City::class).select {
            innerJoin<PetType> { "${t(Templates.alias(PetType::class))}.id = ${t(Templates.alias(City::class))}.id" }
        }.resultCount
        count shouldBe 5L
    }

    @Test
    fun `select entities with template join in the block`() {
        // City ids are 1-6, pet type ids are 0-5: the matching ids are 1-5.
        val count = orm.entity(City::class).select {
            innerJoin({ "pet_type" }, "pt") { "pt.id = ${t(Templates.alias(City::class))}.id" }
        }.resultCount
        count shouldBe 5L
    }

    @Test
    fun `select entities with JoinType template join in the block`() {
        // A left join keeps all six cities whether or not a pet type id matches.
        val count = orm.entity(City::class).select {
            join(JoinType.left(), { "pet_type" }, "pt") { "pt.id = ${t(Templates.alias(City::class))}.id" }
        }.resultCount
        count shouldBe 6L
    }

    @Test
    fun `select entities with subquery join in the block`() {
        // A self-join through the subquery matches every city exactly once.
        val subquery = orm.entity(City::class).select()
        val count = orm.entity(City::class).select {
            join(JoinType.inner(), subquery, "sub") { "sub.id = ${t(Templates.alias(City::class))}.id" }
        }.resultCount
        count shouldBe 6L
    }

    @Test
    fun `select entities with template cross join in the block`() {
        // 6 cities times 6 pet types is 36 rows.
        val count = orm.entity(City::class).select {
            crossJoin { "pet_type" }
        }.resultCount
        count shouldBe 36L
    }

    @Test
    fun `delete entity with where predicate`() {
        val visitRepository = orm.entity(Visit::class)
        val idPath = metamodel<Visit, Int>(visitRepository.model, "id")
        val affected = visitRepository.delete { where(idPath eq 14) }.executeUpdate()
        affected shouldBe 1
        visitRepository.count() shouldBe 13
    }

    // ProjectionRepository: select { } block DSL

    @Test
    fun `select all projections`() {
        val ownerViewRepository = orm.projection(OwnerView::class)
        val views = ownerViewRepository.select().resultList
        views shouldHaveSize 10
    }

    @Test
    fun `select projection with where predicate`() {
        val ownerViewRepository = orm.projection(OwnerView::class)
        val idPath = metamodel<OwnerView, Int>(ownerViewRepository.model, "id")
        val view = ownerViewRepository.select { where(idPath eq 1) }.singleResult
        view.id shouldBe 1
        view.firstName shouldBe "Betty"
    }

    @Test
    fun `select projections with orderBy and limit`() {
        val ownerViewRepository = orm.projection(OwnerView::class)
        val lastNamePath = metamodel<OwnerView, String>(ownerViewRepository.model, "last_name")
        val views = ownerViewRepository.select {
            orderBy(lastNamePath)
            limit(3)
        }.resultList
        views shouldHaveSize 3
        views[0].lastName shouldBe "Black"
        views[1].lastName shouldBe "Coleman"
        views[2].lastName shouldBe "Davis"
    }

    // SqlScope: whereAny, orderByAny, orderByDescendingAny

    @Test
    fun `select with a relaxed where predicate`() {
        val cityRepository = orm.entity(City::class)
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val cities = cityRepository.select {
            where(namePath eq "Madison")
        }.resultList
        cities shouldHaveSize 1
        cities[0].name shouldBe "Madison"
    }

    @Test
    fun `select with a relaxed orderBy`() {
        val cityRepository = orm.entity(City::class)
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val cities = cityRepository.select {
            orderBy(namePath)
        }.resultList
        cities shouldHaveSize 6
        cities[0].name shouldBe "Madison"
    }

    @Test
    fun `select with a relaxed orderByDescending`() {
        val cityRepository = orm.entity(City::class)
        val namePath = metamodel<City, String>(cityRepository.model, "name")
        val cities = cityRepository.select {
            orderByDescending(namePath)
        }.resultList
        cities shouldHaveSize 6
        cities[0].name shouldBe "Windsor"
    }
}
