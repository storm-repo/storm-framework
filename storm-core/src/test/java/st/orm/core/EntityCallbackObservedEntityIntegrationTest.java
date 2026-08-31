package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.EntityCallback;
import st.orm.Ref;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.Visit;
import st.orm.core.template.ORMTemplate;

/**
 * Covers what the "after" callbacks observe: the entity as sent for the methods that return nothing, the entity
 * carrying its generated primary key for the {@code *AndFetchId} methods, and the row read back for the
 * {@code *AndFetch} methods.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@JdbcTest
public class EntityCallbackObservedEntityIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private ORMTemplate ormObserving(List<City> observed) {
        return ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<City>() {
            @Override
            public void afterInsert(City entity) {
                observed.add(entity);
            }
        });
    }

    private Owner newOwner(String firstName, String lastName) {
        return Owner.builder()
                .firstName(firstName)
                .lastName(lastName)
                .address(Address.builder()
                        .address("110 W. Liberty St.")
                        .city(City.builder().id(1).name("Sun Paririe").build())
                        .build())
                .telephone("6085551023")
                .build();
    }

    //
    // Insert: the three tiers.
    //

    @Test
    public void testInsertObservesEntityAsSent() {
        List<City> observed = new ArrayList<>();
        ormObserving(observed).entity(City.class).insert(City.builder().name("Sent").build());
        assertEquals(1, observed.size());
        // The method reports nothing, so no key is read and none is reported to the callback.
        assertNull(observed.getFirst().id());
    }

    @Test
    public void testInsertAndFetchIdObservesGeneratedPrimaryKey() {
        List<City> observed = new ArrayList<>();
        var id = ormObserving(observed).entity(City.class).insertAndFetchId(City.builder().name("Identified").build());
        assertEquals(1, observed.size());
        assertEquals(id, observed.getFirst().id());
        assertEquals("Identified", observed.getFirst().name());
    }

    @Test
    public void testInsertAndFetchObservesFetchedEntity() {
        List<City> observed = new ArrayList<>();
        var inserted = ormObserving(observed).entity(City.class).insertAndFetch(City.builder().name("Fetched").build());
        assertEquals(1, observed.size());
        assertEquals(inserted, observed.getFirst());
        assertNotNull(observed.getFirst().id());
    }

    //
    // Insert: batch tiers.
    //

    @Test
    public void testBatchInsertObservesEntitiesAsSent() {
        List<City> observed = new ArrayList<>();
        ormObserving(observed).entity(City.class).insert(List.of(
                City.builder().name("Batch sent one").build(),
                City.builder().name("Batch sent two").build()));
        assertEquals(2, observed.size());
        assertTrue(observed.stream().allMatch(city -> city.id() == null));
    }

    @Test
    public void testBatchInsertAndFetchIdsObservesGeneratedPrimaryKeys() {
        List<City> observed = new ArrayList<>();
        var ids = ormObserving(observed).entity(City.class).insertAndFetchIds(List.of(
                City.builder().name("Batch keyed one").build(),
                City.builder().name("Batch keyed two").build()));
        assertEquals(2, observed.size());
        assertEquals(ids, observed.stream().map(City::id).toList());
    }

    @Test
    public void testBatchInsertAndFetchObservesFetchedEntities() {
        List<City> observed = new ArrayList<>();
        var inserted = ormObserving(observed).entity(City.class).insertAndFetch(List.of(
                City.builder().name("Batch fetched one").build(),
                City.builder().name("Batch fetched two").build()));
        assertEquals(2, observed.size());
        assertEquals(
                inserted.stream().map(City::id).sorted().toList(),
                observed.stream().map(City::id).sorted().toList());
    }

    //
    // Update: a database-applied change is only observed by the method that reads the row back.
    //

    @Test
    public void testUpdateObservesEntityAsSent() {
        List<Owner> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Owner>() {
            @Override
            public void afterUpdate(Owner entity) {
                observed.add(entity);
            }
        });
        var owners = orm.entity(Owner.class);
        var owner = owners.getById(1);
        owners.update(owner.toBuilder().telephone("1111111111").build());
        assertEquals(1, observed.size());
        // The version column is incremented by the database; the entity as sent still carries the old value.
        assertEquals(owner.version(), observed.getFirst().version());
    }

    @Test
    public void testUpdateAndFetchObservesFetchedEntity() {
        List<Owner> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Owner>() {
            @Override
            public void afterUpdate(Owner entity) {
                observed.add(entity);
            }
        });
        var owners = orm.entity(Owner.class);
        var owner = owners.getById(1);
        var updated = owners.updateAndFetch(owner.toBuilder().telephone("2222222222").build());
        assertEquals(1, observed.size());
        assertEquals(updated.version(), observed.getFirst().version());
        assertEquals(owner.version() + 1, observed.getFirst().version());
    }

    //
    // Write sets report on the same terms, so a callback does not learn a key from the shape of the graph.
    //

    @Test
    public void testWriteSetInsertWithholdsGeneratedKeys() {
        List<Owner> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Owner>() {
            @Override
            public void afterInsert(Owner entity) {
                observed.add(entity);
            }
        });
        var owner = newOwner("Withheld", "Key");
        var pet = Pet.builder()
                .name("Dependent")
                .birthDate(LocalDate.of(2023, 1, 1))
                .type(Ref.of(PetType.class, 1))
                .owner(owner)
                .build();
        // The pet depends on the owner, so the write set retrieves the owner's key to bind the foreign key. The
        // caller asked for nothing back, so that key stays out of the callback.
        orm.writeSet().insert(List.of(pet));
        assertEquals(1, observed.size());
        assertNull(observed.getFirst().id());
    }

    @Test
    public void testWriteSetUpdateAndFetchObservesFetchedEntity() {
        List<Owner> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Owner>() {
            @Override
            public void afterUpdate(Owner entity) {
                observed.add(entity);
            }
        });
        var owner = orm.entity(Owner.class).getById(1);
        var updated = orm.writeSet().updateAndFetch(List.of(owner.toBuilder().telephone("4444444444").build()));
        assertEquals(1, observed.size());
        // The version column is incremented by the database, so only a row read back carries the new value.
        assertEquals(owner.version() + 1, observed.getFirst().version());
        assertEquals(((Owner) updated.getFirst()).version(), observed.getFirst().version());
    }

    @Test
    public void testWriteSetInsertAndFetchObservesFetchedEntity() {
        List<Visit> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Visit>() {
            @Override
            public void afterInsert(Visit entity) {
                observed.add(entity);
            }
        });
        var pet = Pet.builder()
                .name("Fetched")
                .birthDate(LocalDate.of(2023, 4, 4))
                .type(Ref.of(PetType.class, 1))
                .owner(newOwner("Fetched", "Row"))
                .build();
        var visit = new Visit(LocalDate.of(2026, 6, 6), "Fetched visit", pet);
        var inserted = orm.writeSet().insertAndFetch(List.of(visit));
        assertEquals(1, observed.size());
        assertEquals(inserted.getFirst(), observed.getFirst());
    }

    @Test
    public void testWriteSetInsertAndFetchObservesFetchedEntityForDiscoveredMembers() {
        List<Owner> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Owner>() {
            @Override
            public void afterInsert(Owner entity) {
                observed.add(entity);
            }
        });
        var owner = newOwner("Discovered", "Fetched");
        var pet = Pet.builder()
                .name("Discovering")
                .birthDate(LocalDate.of(2023, 5, 5))
                .type(Ref.of(PetType.class, 1))
                .owner(owner)
                .build();
        // Only the pet is passed; the owner joins by discovery and is never reported to the caller. Its callback
        // observes a row read back all the same, so discovery does not change what a callback sees.
        orm.writeSet().insertAndFetch(List.of(pet));
        assertEquals(1, observed.size());
        assertNotNull(observed.getFirst().id());
        assertEquals(observed.getFirst(), orm.entity(Owner.class).getById(observed.getFirst().id()));
    }

    @Test
    public void testWriteSetInsertAndFetchIdsObservesGeneratedKeys() {
        List<Visit> observed = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource).withEntityCallback(new EntityCallback<Visit>() {
            @Override
            public void afterInsert(Visit entity) {
                observed.add(entity);
            }
        });
        var pet = Pet.builder()
                .name("Reported")
                .birthDate(LocalDate.of(2023, 2, 2))
                .type(Ref.of(PetType.class, 1))
                .owner(newOwner("Reported", "Key"))
                .build();
        var visit = new Visit(LocalDate.of(2026, 5, 5), "Reported visit", pet);
        var ids = orm.writeSet().insertAndFetchIds(List.of(visit));
        assertEquals(1, observed.size());
        assertEquals(ids.getFirst(), observed.getFirst().id());
    }
}
