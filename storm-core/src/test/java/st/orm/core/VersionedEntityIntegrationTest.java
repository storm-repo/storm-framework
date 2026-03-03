package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Ref;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.Visit;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests for version-aware entity operations. These tests exercise
 * the SetProcessor.compileVersion branches for integer and timestamp version types,
 * and the ModelImpl.forEachValueOrdered branches for entities with @FK and @Inline fields.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class VersionedEntityIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // Owner: @Version int

    @Test
    public void testUpdateOwnerIncrementsIntVersion() {
        var orm = ORMTemplate.of(dataSource);
        var owners = orm.entity(Owner.class);
        Owner original = owners.getById(1);
        assertNotNull(original);
        int originalVersion = original.version();
        Owner updated = original.toBuilder()
                .firstName("UpdatedBetty")
                .build();
        owners.update(updated);
        Owner fetched = owners.getById(1);
        assertEquals("UpdatedBetty", fetched.firstName());
        // Version should have been incremented by the version-aware SET clause.
        assertEquals(originalVersion + 1, fetched.version());
    }

    @Test
    public void testBatchUpdateOwnerIncrementsVersion() {
        var orm = ORMTemplate.of(dataSource);
        var owners = orm.entity(Owner.class);
        Owner owner1 = owners.getById(1);
        Owner owner2 = owners.getById(2);
        int version1Before = owner1.version();
        int version2Before = owner2.version();
        owners.update(List.of(
                owner1.toBuilder().firstName("BatchBetty").build(),
                owner2.toBuilder().firstName("BatchGeorge").build()
        ));
        Owner fetched1 = owners.getById(1);
        Owner fetched2 = owners.getById(2);
        assertEquals("BatchBetty", fetched1.firstName());
        assertEquals("BatchGeorge", fetched2.firstName());
        assertEquals(version1Before + 1, fetched1.version());
        assertEquals(version2Before + 1, fetched2.version());
    }

    // Visit: @Version Instant

    @Test
    public void testUpdateVisitSetsTimestampVersion() {
        var orm = ORMTemplate.of(dataSource);
        var visits = orm.entity(Visit.class);
        Visit original = visits.getById(1);
        assertNotNull(original);
        Instant originalTimestamp = original.timestamp();
        Visit updated = original.toBuilder()
                .description("Updated rabies shot")
                .build();
        visits.update(updated);
        Visit fetched = visits.getById(1);
        assertEquals("Updated rabies shot", fetched.description());
        // Timestamp version should have been updated by CURRENT_TIMESTAMP in the SET clause.
        assertNotNull(fetched.timestamp());
    }

    @Test
    public void testInsertVisitWithTimestampVersion() {
        var orm = ORMTemplate.of(dataSource);
        var visits = orm.entity(Visit.class);
        long countBefore = visits.count();
        Pet pet = orm.entity(Pet.class).getById(1);
        Visit newVisit = Visit.builder()
                .visitDate(LocalDate.of(2024, 1, 1))
                .description("Checkup")
                .pet(pet)
                .timestamp(Instant.now())
                .build();
        Integer insertedId = visits.insertAndFetchId(newVisit);
        assertNotNull(insertedId);
        assertEquals(countBefore + 1, visits.count());
        Visit fetched = visits.getById(insertedId);
        assertEquals("Checkup", fetched.description());
    }

    // Owner with inline Address: exercises forEachValueOrdered with FK inside inline

    @Test
    public void testInsertOwnerWithInlineAddress() {
        var orm = ORMTemplate.of(dataSource);
        var owners = orm.entity(Owner.class);
        City city = orm.entity(City.class).getById(1);
        long countBefore = owners.count();
        Owner newOwner = Owner.builder()
                .firstName("TestFirst")
                .lastName("TestLast")
                .address(new Address("123 Test St.", city))
                .telephone("5551234")
                .version(0)
                .build();
        Integer insertedId = owners.insertAndFetchId(newOwner);
        assertNotNull(insertedId);
        assertEquals(countBefore + 1, owners.count());
        Owner fetched = owners.getById(insertedId);
        assertEquals("TestFirst", fetched.firstName());
        assertEquals("123 Test St.", fetched.address().address());
    }

    @Test
    public void testUpdateOwnerWithInlineAddressChange() {
        var orm = ORMTemplate.of(dataSource);
        var owners = orm.entity(Owner.class);
        Owner original = owners.getById(1);
        City newCity = orm.entity(City.class).getById(3);
        Owner updated = original.toBuilder()
                .address(new Address("999 New Ave.", newCity))
                .build();
        owners.update(updated);
        Owner fetched = owners.getById(1);
        assertEquals("999 New Ave.", fetched.address().address());
    }

    // Pet: exercises @FK with Ref and @Persist(updatable = false)

    @Test
    public void testUpdatePetWithRefForeignKey() {
        var orm = ORMTemplate.of(dataSource);
        var pets = orm.entity(Pet.class);
        Pet original = pets.getById(1);
        assertNotNull(original);
        Owner newOwner = orm.entity(Owner.class).getById(2);
        Pet updated = original.toBuilder()
                .name("UpdatedLeo")
                .owner(newOwner)
                .build();
        pets.update(updated);
        Pet fetched = pets.getById(1);
        assertEquals("UpdatedLeo", fetched.name());
    }

    @Test
    public void testInsertPetWithNullOwner() {
        var orm = ORMTemplate.of(dataSource);
        var pets = orm.entity(Pet.class);
        long countBefore = pets.count();
        Pet newPet = Pet.builder()
                .name("Orphan")
                .birthDate(LocalDate.of(2024, 6, 15))
                .type(Ref.of(PetType.class, 0))
                .owner(null)
                .build();
        Integer insertedId = pets.insertAndFetchId(newPet);
        assertNotNull(insertedId);
        assertEquals(countBefore + 1, pets.count());
        Pet fetched = pets.getById(insertedId);
        assertEquals("Orphan", fetched.name());
    }

    // Batch insert with version

    @Test
    public void testBatchInsertOwnersWithVersion() {
        var orm = ORMTemplate.of(dataSource);
        var owners = orm.entity(Owner.class);
        City city = orm.entity(City.class).getById(1);
        long countBefore = owners.count();
        owners.insert(List.of(
                Owner.builder().firstName("Batch1").lastName("Last1").address(new Address("addr1", city)).version(0).build(),
                Owner.builder().firstName("Batch2").lastName("Last2").address(new Address("addr2", city)).version(0).build()
        ));
        assertEquals(countBefore + 2, owners.count());
    }

    // Single-table polymorphic insert triggers forEachSealedEntityValue

    @Test
    public void testInsertSingleTableCatTriggersDiscriminatorValue() {
        var orm = ORMTemplate.of(dataSource);
        var animals = orm.entity(st.orm.core.model.polymorphic.Animal.class);
        long countBefore = animals.count();
        animals.insert(new st.orm.core.model.polymorphic.Cat(null, "VersionTestCat", true));
        assertEquals(countBefore + 1, animals.count());
        var allAnimals = animals.select().getResultList();
        var lastAnimal = allAnimals.getLast();
        assertTrue(lastAnimal instanceof st.orm.core.model.polymorphic.Cat);
        assertEquals("VersionTestCat", ((st.orm.core.model.polymorphic.Cat) lastAnimal).name());
    }

    @Test
    public void testUpdateSingleTableDogTriggersDiscriminatorValue() {
        var orm = ORMTemplate.of(dataSource);
        var animals = orm.entity(st.orm.core.model.polymorphic.Animal.class);
        var dog = new st.orm.core.model.polymorphic.Dog(3, "UpdatedRex", 35);
        animals.update(dog);
        var fetched = animals.getById(3);
        assertTrue(fetched instanceof st.orm.core.model.polymorphic.Dog);
        assertEquals("UpdatedRex", ((st.orm.core.model.polymorphic.Dog) fetched).name());
        assertEquals(35, ((st.orm.core.model.polymorphic.Dog) fetched).weight());
    }

    // Batch update single-table polymorphic: mixed subtypes

    @Test
    public void testBatchUpdateMixedPolymorphicSubtypes() {
        var orm = ORMTemplate.of(dataSource);
        var animals = orm.entity(st.orm.core.model.polymorphic.Animal.class);
        animals.update(List.of(
                new st.orm.core.model.polymorphic.Cat(1, "BatchWhiskers", false),
                new st.orm.core.model.polymorphic.Dog(3, "BatchRex", 40)
        ));
        var cat = animals.getById(1);
        assertTrue(cat instanceof st.orm.core.model.polymorphic.Cat);
        assertEquals("BatchWhiskers", ((st.orm.core.model.polymorphic.Cat) cat).name());
        var dog = animals.getById(3);
        assertTrue(dog instanceof st.orm.core.model.polymorphic.Dog);
        assertEquals(40, ((st.orm.core.model.polymorphic.Dog) dog).weight());
    }
}
