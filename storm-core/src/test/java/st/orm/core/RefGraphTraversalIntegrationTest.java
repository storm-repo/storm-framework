package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;
import static st.orm.core.template.TemplateString.raw;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Metamodel;
import st.orm.Navigable;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.TypedMetamodel;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetOwnerCityRef;
import st.orm.core.model.PetOwnerCityRef_;
import st.orm.core.model.PetOwnerRef;
import st.orm.core.model.PetOwnerRef_;
import st.orm.core.model.Pet_;
import st.orm.core.model.SelfRefNode;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlInterceptor;

/**
 * Verifies that queries can navigate beyond a Ref foreign key (filter, order, select) while the reference itself stays
 * an unloaded foreign key column when the root entity is selected. PetOwnerRef maps the pet table and declares
 * {@code owner} as {@code Ref<Owner>}; Pet maps the same table with {@code owner} as an entity, giving an equivalent
 * entity-graph baseline to compare against.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RefGraphTraversalIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testFilterThroughRefMatchesEntityPath() {
        var orm = ORMTemplate.of(dataSource);
        // Baseline: filter pets by their owner's city through an entity foreign key (Pet.owner is an Owner).
        List<Integer> viaEntity = orm.entity(Pet.class).select()
                .where(Pet_.owner.address.city.name, EQUALS, "Madison")
                .getResultList().stream().map(Pet::id).sorted().toList();
        // Same filter, but PetOwnerRef.owner is a Ref<Owner>: the path crosses the reference boundary, which must
        // materialize the join and filter identically.
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        List<Integer> viaRef = orm.entity(PetOwnerRef.class).select()
                .where(cityName, EQUALS, "Madison")
                .getResultList().stream().map(PetOwnerRef::id).sorted().toList();
        assertFalse(viaEntity.isEmpty());
        assertEquals(viaEntity, viaRef);
    }

    @Test
    public void testRootSelectionKeepsOwnerAsUnloadedRef() {
        var orm = ORMTemplate.of(dataSource);
        // Selecting the root still selects owner as its foreign key column: a Ref, not a hydrated Owner.
        List<PetOwnerRef> pets = orm.entity(PetOwnerRef.class).select().getResultList();
        assertFalse(pets.isEmpty());
        PetOwnerRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        Ref<Owner> ownerRef = withOwner.owner();
        assertFalse(ownerRef.isLoaded());
        assertNotNull(ownerRef.id());
    }

    @Test
    public void testOrderByThroughRef() {
        var orm = ORMTemplate.of(dataSource);
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        // Ordering through a reference must materialize the join and sort without error.
        List<Integer> ordered = orm.entity(PetOwnerRef.class).select()
                .where(cityName, EQUALS, "Madison")
                .orderBy(cityName)
                .getResultList().stream().map(PetOwnerRef::id).toList();
        assertFalse(ordered.isEmpty());
    }

    @Test
    public void testSelectColumnThroughRef() {
        record CityName(String name) {}
        var orm = ORMTemplate.of(dataSource);
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        // A custom select template referencing a beyond-reference column must add the join and select the column.
        List<String> names = orm.selectFrom(PetOwnerRef.class, CityName.class, raw("\0", cityName))
                .where(cityName, EQUALS, "Madison")
                .getResultList().stream().map(CityName::name).toList();
        assertEquals(4, names.size());
        assertTrue(names.stream().allMatch("Madison"::equals));
    }

    @Test
    public void testTypedNodeInSelectTemplateMaterializesJoins() {
        record CityName(@Nullable String name) {}
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        // A typed navigation-only node interpolated into a select template is the only element referencing the path,
        // so the joins beyond the reference must still be derived for it.
        SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.selectFrom(PetOwnerRef.class, CityName.class,
                        raw("\0", PetOwnerRef_.owner.address.city.name)).getResultList());
        String sql = observed.getLast();
        assertTrue(sql.contains("join owner"), sql);
        assertTrue(sql.contains("join city"), sql);
    }

    @Test
    public void testUnreferencedRefIsNotJoined() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        // Selecting the root without navigating beyond the reference must not join the owner or city tables; the
        // reference is selected as its foreign key column and its auto-join is pruned.
        SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerRef.class).select().getResultList());
        assertFalse(observed.isEmpty());
        String sql = observed.getLast();
        assertFalse(sql.contains(" join owner"), sql);
        assertFalse(sql.contains(" join city"), sql);
    }

    @Test
    public void testTypedMetamodelFilterThroughRef() {
        var orm = ORMTemplate.of(dataSource);
        // The generated, typed metamodel navigates beyond the Ref: PetOwnerRef_.owner is a reference metamodel and
        // .address.city.name continues as navigation-only nodes. This must filter identically to the entity path.
        List<Integer> viaEntity = orm.entity(Pet.class).select()
                .where(Pet_.owner.address.city.name, EQUALS, "Madison")
                .getResultList().stream().map(Pet::id).sorted().toList();
        List<Integer> viaTypedRef = orm.entity(PetOwnerRef.class).select()
                .where(PetOwnerRef_.owner.address.city.name, EQUALS, "Madison")
                .orderBy(PetOwnerRef_.owner.address.city.name)
                .getResultList().stream().map(PetOwnerRef::id).sorted().toList();
        assertFalse(viaEntity.isEmpty());
        assertEquals(viaEntity, viaTypedRef);
    }

    @Test
    public void testBeyondRefNodeIsNavigableOnly() {
        // The reference node itself is a value metamodel (getValue returns the Ref, so getResultGroupedByRef works),
        // but nodes beyond the reference are navigation-only: not TypedMetamodel, so value operations like
        // getResultGroupedBy do not compile against them. This asserts that contract at the type level.
        // The assignments are the contract: the reference node is value-extractable, so it can be held as a
        // TypedMetamodel, while a node beyond the reference can only be held as a Navigable. The runtime checks then
        // confirm the beyond-reference node is not a value metamodel.
        TypedMetamodel<PetOwnerRef, Owner, Ref<Owner>> referenceNode = PetOwnerRef_.owner;
        assertNotNull(referenceNode);
        Navigable<PetOwnerRef, String> beyond = PetOwnerRef_.owner.address.city.name;
        assertFalse(beyond instanceof Metamodel);
        assertFalse(beyond instanceof TypedMetamodel);
    }

    @Test
    public void testReferencedRefIsJoined() {
        var orm = ORMTemplate.of(dataSource);
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        List<String> observed = new ArrayList<>();
        // Filtering beyond the reference must materialize the owner and city joins.
        SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerRef.class).select().where(cityName, EQUALS, "Madison").getResultList());
        String sql = observed.getLast();
        assertTrue(sql.contains("join owner"), sql);
        assertTrue(sql.contains("join city"), sql);
    }

    @Test
    public void testGroupByThroughRef() {
        // City is nullable here: pets without an owner produce a null-city group through the left join.
        record CityCount(@Nullable String city, long count) {}
        var orm = ORMTemplate.of(dataSource);
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        long madisonPets = orm.entity(PetOwnerRef.class).select()
                .where(cityName, EQUALS, "Madison")
                .getResultList().size();
        // Group pets by the owner's city name. The typed navigation node past the owner reference is accepted by
        // groupBy and materializes the owner and city joins.
        List<CityCount> grouped = orm.entity(PetOwnerRef.class)
                .select(CityCount.class, raw("\0, COUNT(*)", cityName))
                .groupBy(PetOwnerRef_.owner.address.city.name)
                .getResultList();
        long madisonGrouped = grouped.stream()
                .filter(row -> "Madison".equals(row.city()))
                .mapToLong(CityCount::count).sum();
        assertTrue(madisonPets > 0);
        assertEquals(madisonPets, madisonGrouped);
    }

    @Test
    public void testHavingThroughRef() {
        record CityCount(String city, long count) {}
        var orm = ORMTemplate.of(dataSource);
        Metamodel<PetOwnerRef, String> cityName = Metamodel.of(PetOwnerRef.class, "owner.address.city.name");
        // HAVING through the reference keeps only the Madison group. Exercises having(Navigable, ...).
        List<CityCount> grouped = orm.entity(PetOwnerRef.class)
                .select(CityCount.class, raw("\0, COUNT(*)", cityName))
                .groupBy(PetOwnerRef_.owner.address.city.name)
                .having(PetOwnerRef_.owner.address.city.name, EQUALS, "Madison")
                .getResultList();
        assertEquals(1, grouped.size());
        assertEquals("Madison", grouped.getFirst().city());
    }

    @Test
    public void testFilterThroughChainedRefs() {
        var orm = ORMTemplate.of(dataSource);
        // Baseline via the entity graph: Pet.owner is an entity and Owner.address.city is an entity.
        List<Integer> viaEntity = orm.entity(Pet.class).select()
                .where(Pet_.owner.address.city.name, EQUALS, "Madison")
                .getResultList().stream().map(Pet::id).sorted().toList();
        // Chained references: PetOwnerCityRef.owner is Ref<OwnerCityRef> and OwnerCityRef.city is Ref<City>, so the
        // path crosses two reference boundaries. It must materialize both joins and filter identically.
        List<Integer> viaChainedRefs = orm.entity(PetOwnerCityRef.class).select()
                .where(PetOwnerCityRef_.owner.city.name, EQUALS, "Madison")
                .getResultList().stream().map(PetOwnerCityRef::id).sorted().toList();
        assertFalse(viaEntity.isEmpty());
        assertEquals(viaEntity, viaChainedRefs);
    }

    @Test
    public void testChainedRefsJoinBothTables() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        // Navigating across two reference boundaries must join both the owner and city tables on demand.
        SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> orm.entity(PetOwnerCityRef.class).select()
                        .where(PetOwnerCityRef_.owner.city.name, EQUALS, "Madison").getResultList());
        String sql = observed.getLast();
        assertTrue(sql.contains("join owner"), sql);
        assertTrue(sql.contains("join city"), sql);
    }

    @Test
    public void testChainedRootSelectionKeepsOwnerUnloaded() {
        var orm = ORMTemplate.of(dataSource);
        // Selecting the root selects owner as its foreign key column: a Ref, not a hydrated OwnerCityRef.
        List<PetOwnerCityRef> pets = orm.entity(PetOwnerCityRef.class).select().getResultList();
        PetOwnerCityRef withOwner = pets.stream().filter(pet -> pet.owner() != null).findFirst().orElseThrow();
        assertFalse(withOwner.owner().isLoaded());
    }

    @Test
    public void testPrimaryKeyThroughRefResolvesToForeignKeyColumnWithoutJoin() {
        var orm = ORMTemplate.of(dataSource);
        List<String> observed = new ArrayList<>();
        // A reference carries the target's primary key, so reaching it resolves to the pet table's own foreign key
        // column rather than joining the owner table to read the key back.
        List<Integer> ids = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> observed.add(sql.statement().toLowerCase()),
                () -> ids.addAll(orm.entity(PetOwnerRef.class).select()
                        .where(PetOwnerRef_.owner.id, EQUALS, 1)
                        .getResultList().stream().map(PetOwnerRef::id).sorted().toList()));
        String sql = observed.getLast();
        assertFalse(sql.contains("join owner"), sql);
        // The reference itself resolves to that same column.
        List<Integer> viaRef = orm.entity(PetOwnerRef.class).select()
                .whereRef(Metamodel.of(PetOwnerRef.class, "owner"), List.of(Ref.of(Owner.class, 1)))
                .getResultList().stream().map(PetOwnerRef::id).sorted().toList();
        assertFalse(ids.isEmpty());
        assertEquals(viaRef, ids);
        // The path means the same thing when the relationship is declared as an entity instead of a reference.
        List<Integer> viaEntityFk = orm.entity(Pet.class).select()
                .where(Pet_.owner.id, EQUALS, 1)
                .getResultList().stream().map(Pet::id).sorted().toList();
        assertEquals(viaEntityFk, ids);
    }

    @Test
    public void testValueExtractionAcrossRefIsRejectedForEveryPath() {
        var orm = ORMTemplate.of(dataSource);
        PetOwnerRef pet = orm.entity(PetOwnerRef.class).select()
                .where(PetOwnerRef_.owner.id, EQUALS, 1).getResultList().getFirst();
        // The primary key resolves to the foreign key column for querying, but reading a value still follows the
        // requested path, which crosses the reference. Every path beyond a reference is rejected alike, so the
        // declared field type is never contradicted by handing back the reference itself.
        Metamodel<PetOwnerRef, Integer> primaryKey = Metamodel.of(PetOwnerRef.class, "owner.id");
        assertEquals(Integer.class, primaryKey.fieldType());
        assertThrows(UnsupportedOperationException.class, () -> primaryKey.getValue(pet));
        Metamodel<PetOwnerRef, String> otherColumn = Metamodel.of(PetOwnerRef.class, "owner.firstName");
        assertThrows(UnsupportedOperationException.class, () -> otherColumn.getValue(pet));
    }

    @Test
    public void testNavigationPastSelfReferentialRefIsRejected() {
        // Navigating past a self-referential reference would join the table to itself, which resolves against the
        // earlier occurrence and yields the wrong row. The path is rejected instead of returning wrong results.
        var exception = assertThrows(PersistenceException.class,
                () -> Metamodel.of(SelfRefNode.class, "parent.parent.id"));
        assertTrue(exception.getMessage().contains("Cannot navigate past the reference"), exception.getMessage());
    }

    @Test
    public void testShallowNavigationPastSelfReferentialRefIsRejected() {
        // A single hop past the reference is rejected for the same reason: parent.id would resolve against the root.
        assertThrows(PersistenceException.class, () -> Metamodel.of(SelfRefNode.class, "parent.id"));
    }

    @Test
    public void testSelfReferentialRefItselfRemainsAddressable() {
        var orm = ORMTemplate.of(dataSource);
        // The reference itself is the foreign key column, so it stays usable: only navigation past it is rejected.
        Metamodel<SelfRefNode, SelfRefNode> parent = Metamodel.of(SelfRefNode.class, "parent");
        List<Integer> ids = orm.entity(SelfRefNode.class).select()
                .whereRef(parent, List.of(Ref.of(SelfRefNode.class, 1)))
                .getResultList().stream().map(SelfRefNode::id).sorted().toList();
        assertEquals(List.of(2), ids);
    }
}
