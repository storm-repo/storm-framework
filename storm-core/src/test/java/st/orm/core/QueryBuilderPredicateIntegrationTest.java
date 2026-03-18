package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN;
import static st.orm.Operator.IN;
import static st.orm.core.template.TemplateString.raw;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.Scrollable;
import st.orm.core.model.City;
import st.orm.core.model.City_;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests targeting uncovered predicate builder, where-builder, and QueryBuilder convenience methods
 * in storm-core.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QueryBuilderPredicateIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // QueryBuilder.where(record) - delegates to predicate.where(record)

    @Test
    public void testWhereWithRecord() {
        var orm = ORMTemplate.of(dataSource);
        City city = orm.selectFrom(City.class)
                .where(City_.id, EQUALS, 1)
                .getSingleResult();
        List<City> results = orm.selectFrom(City.class)
                .where(city)
                .getResultList();
        assertEquals(1, results.size());
        assertEquals(city.id(), results.getFirst().id());
    }

    // QueryBuilder.where(path, Iterable) - delegates to where(path, IN, it)

    @Test
    public void testWhereWithPathAndIterable() {
        var orm = ORMTemplate.of(dataSource);
        Pet pet1 = orm.selectFrom(Pet.class).where(Pet_.id, EQUALS, 1).getSingleResult();
        Pet pet2 = orm.selectFrom(Pet.class).where(Pet_.id, EQUALS, 2).getSingleResult();
        List<Visit> visits = orm.selectFrom(Visit.class)
                .where(Visit_.pet, List.of(pet1, pet2))
                .getResultList();
        assertTrue(visits.size() >= 2, "Expected at least 2 visits for pets 1 and 2");
        for (Visit visit : visits) {
            int petId = visit.pet().id();
            assertTrue(petId == 1 || petId == 2, "Visit pet should be 1 or 2, got " + petId);
        }
    }

    // QueryBuilder.whereRef(path, Iterable<Ref>) - delegates to predicate.whereRef(path, it)

    @Test
    public void testWhereRefWithPathAndIterable() {
        var orm = ORMTemplate.of(dataSource);
        Ref<Owner> ownerRef1 = Ref.of(Owner.class, 1);
        Ref<Owner> ownerRef2 = Ref.of(Owner.class, 2);
        List<Pet> pets = orm.selectFrom(Pet.class)
                .whereRef(Pet_.owner, List.of(ownerRef1, ownerRef2))
                .getResultList();
        assertTrue(pets.size() >= 2, "Expected at least 2 pets for owners 1 and 2");
        for (Pet pet : pets) {
            int ownerId = pet.owner().id();
            assertTrue(ownerId == 1 || ownerId == 2, "Pet owner should be 1 or 2, got " + ownerId);
        }
    }

    // QueryBuilder.where(path, Ref) - delegates to predicate.where(path, ref)

    @Test
    public void testWhereWithPathAndRef() {
        var orm = ORMTemplate.of(dataSource);
        Ref<Owner> ownerRef = Ref.of(Owner.class, 6);
        List<Pet> pets = orm.selectFrom(Pet.class)
                .where(Pet_.owner, ownerRef)
                .getResultList();
        for (Pet pet : pets) {
            assertEquals(6, pet.owner().id());
        }
    }

    // WhereBuilder.whereRef(Ref) - match by ref on primary table

    @Test
    public void testWhereBuilderWhereRef() {
        var orm = ORMTemplate.of(dataSource);
        Ref<City> cityRef = Ref.of(City.class, 3);
        List<City> cities = orm.selectFrom(City.class)
                .where(predicate -> predicate.whereRef(cityRef))
                .getResultList();
        assertEquals(1, cities.size());
        assertEquals(3, cities.getFirst().id());
    }

    // WhereBuilder.where(record) - match by record on primary table

    @Test
    public void testWhereBuilderWhereRecord() {
        var orm = ORMTemplate.of(dataSource);
        City city = orm.selectFrom(City.class)
                .where(City_.id, EQUALS, 1)
                .getSingleResult();
        List<City> cities = orm.selectFrom(City.class)
                .where(predicate -> predicate.where(city))
                .getResultList();
        assertEquals(1, cities.size());
        assertEquals(city.name(), cities.getFirst().name());
    }

    // WhereBuilder.whereId(Iterable) - match by collection of ids
    // Must use .typed(Integer.class) to resolve the wildcard ID type.

    @Test
    public void testWhereBuilderWhereIdIterable() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .typed(Integer.class)
                .where(predicate -> predicate.whereId(List.of(1, 3, 5)))
                .getResultList();
        assertEquals(3, cities.size());
    }

    // WhereBuilder.whereRef(Iterable<Ref>) - match by collection of refs

    @Test
    public void testWhereBuilderWhereRefIterable() {
        var orm = ORMTemplate.of(dataSource);
        Ref<City> ref1 = Ref.of(City.class, 2);
        Ref<City> ref4 = Ref.of(City.class, 4);
        List<City> cities = orm.selectFrom(City.class)
                .where(predicate -> predicate.whereRef(List.of(ref1, ref4)))
                .getResultList();
        assertEquals(2, cities.size());
    }

    // WhereBuilder.where(path, Ref) on related table

    @Test
    public void testWhereBuilderWherePathRef() {
        var orm = ORMTemplate.of(dataSource);
        Ref<Owner> ownerRef = Ref.of(Owner.class, 3);
        List<Pet> pets = orm.selectFrom(Pet.class)
                .where(predicate -> predicate.where(Pet_.owner, ownerRef))
                .getResultList();
        for (Pet pet : pets) {
            assertEquals(3, pet.owner().id());
        }
    }

    // WhereBuilder.whereRef(path, Iterable<Ref>) on related table

    @Test
    public void testWhereBuilderWhereRefPathIterable() {
        var orm = ORMTemplate.of(dataSource);
        Ref<Owner> ref1 = Ref.of(Owner.class, 1);
        Ref<Owner> ref2 = Ref.of(Owner.class, 2);
        List<Pet> pets = orm.selectFrom(Pet.class)
                .where(predicate -> predicate.whereRef(Pet_.owner, List.of(ref1, ref2)))
                .getResultList();
        assertTrue(pets.size() >= 2);
        for (Pet pet : pets) {
            int ownerId = pet.owner().id();
            assertTrue(ownerId == 1 || ownerId == 2);
        }
    }

    // WhereBuilder.where(path, operator, Iterable) on related table

    @Test
    public void testWhereBuilderWherePathOperatorIterable() {
        var orm = ORMTemplate.of(dataSource);
        List<Pet> pets = orm.selectFrom(Pet.class)
                .where(predicate -> predicate.where(Pet_.id, IN, List.of(1, 2, 3)))
                .getResultList();
        assertEquals(3, pets.size());
    }

    // PredicateBuilder.and(TemplateString) - adds raw template as AND clause

    @Test
    public void testPredicateBuilderAndTemplate() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .where(predicate -> predicate.where(City_.id, GREATER_THAN, 0)
                        .and(raw("\0 LIKE 'M%'", City_.name)))
                .getResultList();
        assertTrue(cities.size() >= 1, "Expected at least 1 city starting with M");
        for (City city : cities) {
            assertTrue(city.name().startsWith("M"), "City name should start with M: " + city.name());
        }
    }

    // PredicateBuilder.or(TemplateString) - adds raw template as OR clause

    @Test
    public void testPredicateBuilderOrTemplate() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .typed(Integer.class)
                .where(predicate -> predicate.whereId(1)
                        .or(raw("\0 = 'Waunakee'", City_.name)))
                .getResultList();
        assertTrue(cities.size() >= 1);
    }

    // PredicateBuilder.andAny - AND with cross-type predicate

    @Test
    public void testPredicateBuilderAndAny() {
        var orm = ORMTemplate.of(dataSource);
        List<Visit> visits = orm.selectFrom(Visit.class)
                .where(predicate -> predicate.where(Visit_.id, GREATER_THAN, 0)
                        .andAny(predicate.where(Visit_.id, IN, List.of(1, 2, 3))))
                .getResultList();
        assertEquals(3, visits.size());
    }

    // PredicateBuilder.orAny - OR with cross-type predicate

    @Test
    public void testPredicateBuilderOrAny() {
        var orm = ORMTemplate.of(dataSource);
        List<Visit> visits = orm.selectFrom(Visit.class)
                .typed(Integer.class)
                .where(predicate -> predicate.whereId(1)
                        .orAny(predicate.whereId(2)))
                .getResultList();
        assertEquals(2, visits.size());
    }

    // QueryBuilder.having with raw template

    @Test
    public void testHavingConvenienceMethod() {
        var orm = ORMTemplate.of(dataSource);
        record PetVisitCount(Pet pet, int visitCount) {}
        var results = orm.selectFrom(Pet.class, PetVisitCount.class, raw("\0, COUNT(*)", Pet.class))
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .having(raw("COUNT(*) > \0", 1))
                .getResultList();
        assertTrue(results.size() > 0, "Expected at least one pet with more than 1 visit");
        for (var result : results) {
            assertTrue(result.visitCount() > 1);
        }
    }

    // QueryBuilder.groupByAny with empty path throws PersistenceException

    @Test
    public void testGroupByAnyEmptyPathThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class, () ->
                orm.selectFrom(City.class).groupByAny());
    }

    // QueryBuilder.orderByAny with empty path throws PersistenceException

    @Test
    public void testOrderByAnyEmptyPathThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class, () ->
                orm.selectFrom(City.class).orderByAny());
    }

    // QueryBuilder.orderByDescending via metamodel

    @Test
    public void testOrderByDescendingMetamodel() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .orderByDescending(City_.id)
                .getResultList();
        assertEquals(6, cities.size());
        for (int i = 0; i < cities.size() - 1; i++) {
            assertTrue(cities.get(i).id() > cities.get(i + 1).id(),
                    "Expected descending order at index " + i);
        }
    }

    // QueryBuilder.scroll with invalid size throws

    @Test
    public void testScrollNonPositiveSizeThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(IllegalArgumentException.class, () ->
                orm.selectFrom(City.class).orderBy(City_.id).scroll(0));
    }

    // QueryBuilder.scroll basic without key

    @Test
    public void testScrollBasicWithoutKey() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .orderBy(City_.id)
                .scroll(3);
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext(), "Expected hasNext=true since there are 6 cities");
    }

    // Scrolling: scrollBefore (cursorless, descending)

    @Test
    public void testScrollBeforeCursorless() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .scroll(Scrollable.of(City_.id, 3).backward());
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext(), "Expected hasNext since there are 6 cities");
        for (int i = 0; i < window.content().size() - 1; i++) {
            assertTrue(window.content().get(i).id() > window.content().get(i + 1).id());
        }
    }

    // Scrolling: scrollAfter with value cursor

    @Test
    public void testScrollAfterWithValueCursor() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .scroll(new Scrollable<>(City_.id, 2, null, null, 3, true));
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext());
        for (City city : window.content()) {
            assertTrue(city.id() > 2);
        }
    }

    // Scrolling: scrollBefore with value cursor

    @Test
    public void testScrollBeforeWithValueCursor() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .scroll(new Scrollable<>(City_.id, 5, null, null, 3, false));
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext());
        for (City city : window.content()) {
            assertTrue(city.id() < 5);
        }
    }

    // Scrolling: scrollAfter/scrollBefore throw with explicit orderBy

    @Test
    public void testScrollAfterThrowsWithExplicitOrderBy() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class, () ->
                orm.selectFrom(City.class)
                        .orderBy(City_.name)
                        .scroll(new Scrollable<>(City_.id, 1, null, null, 3, true)));
    }

    @Test
    public void testScrollBeforeThrowsWithExplicitOrderBy() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class, () ->
                orm.selectFrom(City.class)
                        .orderBy(City_.name)
                        .scroll(new Scrollable<>(City_.id, 5, null, null, 3, false)));
    }

    // Composite scrolling: first page

    @Test
    public void testCompositeScrollFirstPage() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .scroll(Scrollable.of(City_.id, City_.name, 3));
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext());
    }

    // Composite scrolling: scroll forward with cursor

    @Test
    public void testCompositeScrollAfter() {
        var orm = ORMTemplate.of(dataSource);
        var firstPage = orm.selectFrom(City.class)
                .scroll(Scrollable.of(City_.id, City_.name, 3));
        City lastCity = firstPage.content().getLast();
        var secondPage = orm.selectFrom(City.class)
                .scroll(new Scrollable<>(City_.id, lastCity.id(), City_.name, lastCity.name(), 3, true));
        assertNotNull(secondPage);
        assertFalse(secondPage.content().isEmpty());
    }

    // Composite scrolling: scroll backward with cursor

    @Test
    public void testCompositeScrollBefore() {
        var orm = ORMTemplate.of(dataSource);
        var lastPage = orm.selectFrom(City.class)
                .scroll(Scrollable.of(City_.id, City_.name, 3).backward());
        assertNotNull(lastPage);
        City firstCity = lastPage.content().getLast();
        var previousPage = orm.selectFrom(City.class)
                .scroll(new Scrollable<>(City_.id, firstCity.id(), City_.name, firstCity.name(), 3, false));
        assertNotNull(previousPage);
    }

    // Composite scrolling: first page descending

    @Test
    public void testCompositeScrollBeforeCursorless() {
        var orm = ORMTemplate.of(dataSource);
        var window = orm.selectFrom(City.class)
                .scroll(Scrollable.of(City_.id, City_.name, 3).backward());
        assertEquals(3, window.content().size());
        assertTrue(window.hasNext());
    }

    // Complex predicate: nested AND and OR

    @Test
    public void testComplexNestedPredicate() {
        var orm = ORMTemplate.of(dataSource);
        // City id=1 is "Sun Paririe", id=2 is "Madison". Only Madison starts with M.
        List<City> cities = orm.selectFrom(City.class)
                .typed(Integer.class)
                .where(predicate ->
                        predicate.whereId(1).or(predicate.whereId(2)))
                .where(predicate ->
                        predicate.where(raw("\0 LIKE 'M%'", City_.name)))
                .getResultList();
        assertEquals(1, cities.size());
        assertEquals("Madison", cities.getFirst().name());
    }

    // QueryBuilder.where(Iterable<T>) - match by collection of records

    @Test
    public void testWhereWithIterableRecords() {
        var orm = ORMTemplate.of(dataSource);
        City city1 = orm.selectFrom(City.class).where(City_.id, EQUALS, 1).getSingleResult();
        City city2 = orm.selectFrom(City.class).where(City_.id, EQUALS, 2).getSingleResult();
        List<City> cities = orm.selectFrom(City.class)
                .where(List.of(city1, city2))
                .getResultList();
        assertEquals(2, cities.size());
    }

    // QueryBuilder.whereId(Iterable) - match by collection of ids

    @Test
    public void testWhereIdIterable() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .typed(Integer.class)
                .whereId(List.of(1, 3, 5))
                .getResultList();
        assertEquals(3, cities.size());
    }

    // QueryBuilder.whereRef(Iterable<Ref>) - match by collection of refs

    @Test
    public void testWhereRefIterable() {
        var orm = ORMTemplate.of(dataSource);
        Ref<City> ref1 = Ref.of(City.class, 1);
        Ref<City> ref2 = Ref.of(City.class, 6);
        List<City> cities = orm.selectFrom(City.class)
                .whereRef(List.of(ref1, ref2))
                .getResultList();
        assertEquals(2, cities.size());
    }

    // QueryBuilder.where(Ref<T>) - match by ref on primary table

    @Test
    public void testWhereRef() {
        var orm = ORMTemplate.of(dataSource);
        Ref<City> cityRef = Ref.of(City.class, 4);
        List<City> cities = orm.selectFrom(City.class)
                .where(cityRef)
                .getResultList();
        assertEquals(1, cities.size());
        assertEquals(4, cities.getFirst().id());
    }

    // QueryBuilder.where(path, operator, Iterable) with explicit operator

    @Test
    public void testWhereWithPathOperatorIterable() {
        var orm = ORMTemplate.of(dataSource);
        List<City> cities = orm.selectFrom(City.class)
                .where(City_.id, IN, List.of(2, 4, 6))
                .getResultList();
        assertEquals(3, cities.size());
        for (City city : cities) {
            assertTrue(city.id() == 2 || city.id() == 4 || city.id() == 6);
        }
    }

    // QueryBuilder.prepare() - delegates to build().prepare()

    @Test
    public void testPrepare() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        try (var prepared = orm.selectFrom(City.class).prepare()) {
            assertNotNull(prepared);
            List<City> cities = prepared.getResultList(City.class);
            assertEquals(6, cities.size());
        }
    }

    // QueryBuilder.whereAny - cross-type where

    @Test
    public void testWhereAnyFunction() {
        var orm = ORMTemplate.of(dataSource);
        List<Visit> visits = orm.selectFrom(Visit.class)
                .typed(Integer.class)
                .whereAny(predicate -> predicate.whereId(1).or(predicate.whereId(2)))
                .getResultList();
        assertEquals(2, visits.size());
    }
}
