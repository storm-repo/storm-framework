package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;

import java.util.ArrayList;
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
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.OwnerCityRef;
import st.orm.core.model.Pet;
import st.orm.core.model.PetOwnerCityRef;
import st.orm.core.model.PetOwnerCityRef_;
import st.orm.core.model.PetOwnerRef;
import st.orm.core.model.PetOwnerRef_;
import st.orm.core.model.Pet_;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlInterceptor;

/**
 * Verifies that a query resolves the references named by {@code fetch}, selecting the referenced table's columns in
 * place of the foreign key column so the reference comes back loaded. PetOwnerRef maps the pet table with
 * {@code owner} as {@code Ref<Owner>}; Pet maps the same table with {@code owner} as an entity, giving an
 * entity-graph baseline to compare the resolved reference against.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RefFetchIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testFetchLoadsReferenceInSameQuery() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        List<PetOwnerRef> pets = SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerRef.class).select()
                        .fetch(PetOwnerRef_.owner)
                        .getResultList());
        assertFalse(pets.isEmpty());
        // One statement resolves every reference: reading them back triggers no further query.
        assertEquals(1, observed.size());
        assertTrue(observed.getFirst().contains("join owner"), observed.getFirst());
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertTrue(withOwner.owner().isLoaded());
        assertNotNull(withOwner.owner().getOrNull());
    }

    @Test
    public void testResolvedReferenceMatchesEntityGraph() {
        var orm = ORMTemplate.of(dataSource);
        // The entity-graph baseline: Pet declares owner as an entity, so it is always hydrated.
        List<Owner> viaEntity = orm.entity(Pet.class).select()
                .where(Pet_.name, EQUALS, "Leo")
                .getResultList().stream().map(Pet::owner).toList();
        List<Owner> viaRef = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getResultList().stream().map(pet -> pet.owner().fetch()).toList();
        assertFalse(viaEntity.isEmpty());
        assertEquals(viaEntity, viaRef);
    }

    @Test
    public void testFetchIsFreeOfQueries() {
        var orm = ORMTemplate.of(dataSource);
        List<PetOwnerRef> pets = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .getResultList();
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        List<String> observed = new ArrayList<>();
        Owner owner = SqlInterceptor.observe(
                sql -> observed.add(sql.statement()),
                () -> withOwner.owner().fetch());
        assertNotNull(owner);
        assertTrue(observed.isEmpty(), () -> "fetch() queried the database: " + observed);
    }

    @Test
    public void testWithoutFetchTheReferenceStaysUnloaded() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        List<PetOwnerRef> pets = SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerRef.class).select().getResultList());
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertFalse(withOwner.owner().isLoaded());
        assertFalse(observed.getFirst().contains("join owner"), observed.getFirst());
    }

    @Test
    public void testFetchThroughTwoReferencesClosesOverThePrefix() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        // Naming only the deeper path resolves the owner as well: the owner record is what holds the city reference.
        List<PetOwnerCityRef> pets = SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerCityRef.class).select()
                        .fetch(PetOwnerCityRef_.owner.city)
                        .getResultList());
        assertEquals(1, observed.size());
        assertTrue(observed.getFirst().contains("join owner"), observed.getFirst());
        assertTrue(observed.getFirst().contains("join city"), observed.getFirst());
        PetOwnerCityRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        Ref<OwnerCityRef> ownerRef = withOwner.owner();
        assertTrue(ownerRef.isLoaded());
        Ref<City> cityRef = ownerRef.fetch().city();
        assertNotNull(cityRef);
        assertTrue(cityRef.isLoaded());
        assertNotNull(cityRef.fetch().name());
    }

    @Test
    public void testFetchOfOuterLevelLeavesInnerReferenceUnloaded() {
        var orm = ORMTemplate.of(dataSource);
        List<PetOwnerCityRef> pets = orm.entity(PetOwnerCityRef.class).select()
                .fetch(PetOwnerCityRef_.owner)
                .getResultList();
        PetOwnerCityRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertTrue(withOwner.owner().isLoaded());
        // The owner's own reference is not part of the plan, so it stays a foreign key column.
        assertFalse(withOwner.owner().fetch().city().isLoaded());
    }

    @Test
    public void testNullableReferenceYieldsNull() {
        var orm = ORMTemplate.of(dataSource);
        List<PetOwnerRef> pets = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Sly")
                .getResultList();
        assertEquals(1, pets.size());
        // Sly has no owner, so the outer join yields no row and the reference is null, as for a nullable entity
        // foreign key.
        assertNull(pets.getFirst().owner());
    }

    @Test
    public void testGetOrThrowReturnsTheResolvedRecordWithoutQuerying() {
        var orm = ORMTemplate.of(dataSource);
        PetOwnerRef pet = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getSingleResult();
        List<String> observed = new ArrayList<>();
        Owner owner = SqlInterceptor.observe(
                sql -> observed.add(sql.statement()),
                () -> pet.owner().getOrThrow());
        assertNotNull(owner);
        assertTrue(observed.isEmpty(), () -> "getOrThrow() queried the database: " + observed);
    }

    @Test
    public void testGetOrThrowFailsWhenTheQueryDidNotResolveTheReference() {
        var orm = ORMTemplate.of(dataSource);
        // The reference is fetchable, so fetch() would silently query here. Asking for what the query was meant to
        // have resolved reports the missing plan instead of paying for it a row at a time.
        PetOwnerRef pet = orm.entity(PetOwnerRef.class).select()
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getSingleResult();
        var exception = assertThrows(PersistenceException.class, () -> pet.owner().getOrThrow());
        assertTrue(exception.getMessage().contains("fetch()"), exception.getMessage());
        assertNotNull(pet.owner().fetch());
    }

    @Test
    public void testValueOfANullableReferenceIsNull() {
        var orm = ORMTemplate.of(dataSource);
        // A reference metamodel is generated per target type, so one class serves every property that references it.
        // A nullable foreign key holds no reference, so the value it reports is null whether or not the query
        // resolved it, which is what the generated accessor declares.
        PetOwnerRef unresolved = orm.entity(PetOwnerRef.class).select()
                .where(PetOwnerRef_.name, EQUALS, "Sly")
                .getSingleResult();
        assertNull(PetOwnerRef_.owner.getValue(unresolved));
        PetOwnerRef resolved = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Sly")
                .getSingleResult();
        assertNull(PetOwnerRef_.owner.getValue(resolved));
    }

    @Test
    public void testResolvedReferenceKeepsIdentityAndUnloads() {
        var orm = ORMTemplate.of(dataSource);
        PetOwnerRef resolved = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getSingleResult();
        PetOwnerRef unresolved = orm.entity(PetOwnerRef.class).select()
                .where(PetOwnerRef_.name, EQUALS, "Leo")
                .getSingleResult();
        // A reference is identified by its key, so resolving it changes nothing about how it compares.
        assertEquals(unresolved.owner(), resolved.owner());
        assertEquals(unresolved.owner().id(), resolved.owner().id());
        assertFalse(resolved.owner().unload().isLoaded());
        assertEquals(resolved.owner().id(), resolved.owner().unload().id());
    }

    @Test
    public void testFetchRejectsPathThatCrossesNoReference() {
        var orm = ORMTemplate.of(dataSource);
        // petType is an entity foreign key: it is already part of the record the query selects.
        var exception = assertThrows(PersistenceException.class,
                () -> orm.entity(PetOwnerRef.class).select().fetch(PetOwnerRef_.petType));
        assertTrue(exception.getMessage().contains("crosses no reference"), exception.getMessage());
    }

    @Test
    public void testGroupingByAResolvedReferenceYieldsLoadedKeys() {
        var orm = ORMTemplate.of(dataSource);
        // Grouping takes the reference straight from the row, so a resolved reference makes the keys carry their
        // record while still comparing by primary key.
        var grouped = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .where(PetOwnerRef_.owner.firstName, EQUALS, "Betty")
                .getResultGroupedByRef(PetOwnerRef_.owner);
        assertFalse(grouped.isEmpty());
        for (Ref<Owner> key : grouped.keySet()) {
            assertTrue(key.isLoaded());
            assertEquals("Betty", key.getOrNull().firstName());
        }
    }

    @Test
    public void testFetchRejectsACustomSelectType() {
        var orm = ORMTemplate.of(dataSource);
        // The query selects a projection of its own, so the paths name references of a record it does not return.
        var exception = assertThrows(PersistenceException.class,
                () -> orm.entity(PetOwnerRef.class).select(String.class).fetch(PetOwnerRef_.owner));
        assertTrue(exception.getMessage().contains("does not select"), exception.getMessage());
    }

    @Test
    public void testFetchRejectsRefResults() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.entity(PetOwnerRef.class).selectRef().fetch(PetOwnerRef_.owner));
    }

    @Test
    public void testFetchSurvivesCompilationIntoAPlan() {
        var orm = ORMTemplate.of(dataSource);
        // A compiled plan is processed once and bound per execution, so the resolved references have to travel with
        // the statement rather than with the builder that produced it.
        var plan = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .plan();
        List<PetOwnerRef> pets = plan.query().getResultList(PetOwnerRef.class);
        assertFalse(pets.isEmpty());
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertTrue(withOwner.owner().isLoaded());
    }

    @Test
    public void testFetchSurvivesPaging() {
        var orm = ORMTemplate.of(dataSource);
        // Paging runs a separate count query whose shape differs from the select; the plan must apply to the select
        // alone and leave the count untouched.
        var page = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .orderBy(PetOwnerRef_.id)
                .page(0, 3);
        assertEquals(3, page.content().size());
        PetOwnerRef withOwner = page.content().stream()
                .filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertTrue(withOwner.owner().isLoaded());
    }

    @Test
    public void testFetchStreamsResolvedReferences() {
        var orm = ORMTemplate.of(dataSource);
        try (var stream = orm.entity(PetOwnerRef.class).select()
                .fetch(PetOwnerRef_.owner)
                .getResultStream()) {
            PetOwnerRef withOwner = stream.filter(pet -> pet.owner() != null).findFirst().orElseThrow();
            assertTrue(withOwner.owner().isLoaded());
        }
    }

    @Test
    public void testFetchCombinesWithPredicatesAndOrdering() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        List<PetOwnerRef> pets = SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerRef.class).select()
                        .fetch(PetOwnerRef_.owner)
                        .where(PetOwnerRef_.owner.firstName, EQUALS, "Betty")
                        .orderBy(PetOwnerRef_.name)
                        .getResultList());
        assertEquals(1, observed.size());
        assertFalse(pets.isEmpty());
        for (PetOwnerRef pet : pets) {
            assertEquals("Betty", pet.owner().fetch().firstName());
        }
    }
}
