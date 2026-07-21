package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.OwnerPrimaryPet;
import st.orm.core.model.Pet;
import st.orm.core.model.PetOwnerRef;
import st.orm.core.model.PetType;
import st.orm.core.model.Specialty;
import st.orm.core.model.Vet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.Visit;
import st.orm.core.repository.EntityRepository;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlInterceptor;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class WriteSetIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private ORMTemplate orm() {
        return ORMTemplate.of(dataSource);
    }

    private Owner newOwner(String firstName, String lastName) {
        return Owner.builder()
                .firstName(firstName)
                .lastName(lastName)
                .address(Address.builder().address("110 W. Liberty St.").city(City.builder().id(1).name("Sun Paririe").build()).build())
                .telephone("6085551023")
                .build();
    }

    private Ref<PetType> dogType() {
        return Ref.of(PetType.class, 1);
    }

    @Test
    public void testInsertThreeLevelGraphWithSharedParent() {
        var orm = orm();
        var owner = newOwner("Alice", "WriteSet");
        var wolfie = Pet.builder().name("Wolfie").birthDate(LocalDate.of(2024, 1, 1)).type(dogType()).owner(owner).build();
        var rex = Pet.builder().name("Rex").birthDate(LocalDate.of(2024, 2, 2)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 7, 14), "Check-up", wolfie);
        List<String> inserts = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> { if (sql.statement().toUpperCase().startsWith("INSERT")) inserts.add(sql.statement()); },
                () -> { orm.writeSet().insert(List.of(wolfie, rex, visit)); return null; });
        // One statement per type per level: owner, pets (batched), visit.
        assertEquals(3, inserts.size());
        // The shared owner instance is inserted exactly once.
        var owners = orm.entity(Owner.class).select().getResultList().stream()
                .filter(fetched -> fetched.lastName().equals("WriteSet"))
                .toList();
        assertEquals(1, owners.size());
        var pets = orm.entity(Pet.class).select().getResultList().stream()
                .filter(pet -> pet.owner() != null && pet.owner().id().equals(owners.getFirst().id()))
                .toList();
        assertEquals(2, pets.size());
        var visits = orm.entity(Visit.class).select().getResultList().stream()
                .filter(fetched -> "Check-up".equals(fetched.description()))
                .toList();
        assertEquals(1, visits.size());
        assertEquals("Wolfie", visits.getFirst().pet().name());
    }

    @Test
    public void testInsertClosurePullsInUnsavedParents() {
        var orm = orm();
        var owner = newOwner("Closure", "Only");
        var pet = Pet.builder().name("Shadow").birthDate(LocalDate.of(2023, 3, 3)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 1, 1), "Closure visit", pet);
        // Only the visit is passed; pet and owner join via the insertion closure.
        orm.writeSet().insert(List.of(visit));
        var fetched = orm.entity(Visit.class).select().getResultList().stream()
                .filter(candidate -> "Closure visit".equals(candidate.description()))
                .toList();
        assertEquals(1, fetched.size());
        assertEquals("Shadow", fetched.getFirst().pet().name());
        assertNotNull(fetched.getFirst().pet().owner());
        assertEquals("Closure", fetched.getFirst().pet().owner().firstName());
    }

    @Test
    public void testInsertAndFetchIdsReturnsKeysInInputOrder() {
        var orm = orm();
        var owner = newOwner("Ids", "Order");
        var pet = Pet.builder().name("Keys").birthDate(LocalDate.of(2022, 4, 4)).type(dogType()).owner(owner).build();
        var first = new Visit(LocalDate.of(2026, 3, 3), "Ids first", pet);
        var second = new Visit(LocalDate.of(2026, 3, 4), "Ids second", pet);
        List<String> statements = new ArrayList<>();
        List<Integer> ids = SqlInterceptor.observe(
                sql -> statements.add(sql.statement().toUpperCase()),
                () -> orm.writeSet().insertAndFetchIds(List.of(first, second)));
        assertEquals(2, ids.size());
        // The keys come from the insert itself; no row is re-read.
        assertTrue(statements.stream().noneMatch(statement -> statement.startsWith("SELECT")));
        var visits = orm.entity(Visit.class);
        assertEquals("Ids first", visits.getById(ids.get(0)).description());
        assertEquals("Ids second", visits.getById(ids.get(1)).description());
    }

    @Test
    public void testInsertAndFetchIdsReportsExplicitMembersOnly() {
        var orm = orm();
        var owner = newOwner("Ids", "Closure");
        var pet = Pet.builder().name("Hidden").birthDate(LocalDate.of(2021, 5, 5)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 4, 4), "Ids closure", pet);
        // Only the visit is passed; pet and owner join via the insertion closure but are not reported.
        List<Integer> ids = orm.writeSet().insertAndFetchIds(List.of(visit));
        assertEquals(1, ids.size());
        var fetched = orm.entity(Visit.class).getById(ids.getFirst());
        assertEquals("Ids closure", fetched.description());
        assertEquals("Hidden", fetched.pet().name());
        assertNotEquals(0, fetched.pet().id());
    }

    @Test
    public void testInsertAndFetchIdSingleEntity() {
        var orm = orm();
        var owner = newOwner("Ids", "Single");
        var pet = Pet.builder().name("Solo").birthDate(LocalDate.of(2020, 6, 6)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 5, 5), "Ids single", pet);
        Integer id = orm.writeSet().insertAndFetchId(visit);
        assertEquals("Ids single", orm.entity(Visit.class).getById(id).description());
    }

    @Test
    public void testUpsertAndFetchIdsUnsavedRequiresDialectSupport() {
        var orm = orm();
        var owner = newOwner("UpsertIds", "Dialect");
        var pet = Pet.builder().name("NoDialect").birthDate(LocalDate.of(2022, 7, 7)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 6, 6), "UpsertIds dialect", pet);
        // An unsaved explicit member needs a real upsert, which the default dialect lacks; the ids
        // variant reports it the same way upsertAndFetch does. The PostgreSQL module covers the
        // insert path and the mixed batch against a dialect with upsert support.
        assertThrows(PersistenceException.class, () -> orm.writeSet().upsertAndFetchIds(List.of(visit)));
    }

    @Test
    public void testUpsertAndFetchIdsUpdatesExistingEntity() {
        var orm = orm();
        var owner = newOwner("UpsertIds", "Update");
        var pet = Pet.builder().name("Known").birthDate(LocalDate.of(2021, 8, 8)).type(dogType()).owner(owner).build();
        var visit = orm.writeSet().insertAndFetch(new Visit(LocalDate.of(2026, 7, 7), "Before amend", pet));
        // The visit carries its key: the upsert takes the update path and reports that same key.
        var amended = visit.toBuilder().description("After amend").build();
        Integer id = orm.writeSet().upsertAndFetchId(amended);
        assertEquals(visit.id(), id);
        assertEquals("After amend", orm.entity(Visit.class).getById(id).description());
    }

    @Test
    public void testInsertAndFetchReturnsInputOrder() {
        var orm = orm();
        var owner = newOwner("Fetch", "Order");
        var pet = Pet.builder().name("First").birthDate(LocalDate.of(2022, 1, 1)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 2, 2), "Fetch visit", pet);
        var fetched = orm.writeSet().insertAndFetch(List.of(visit, pet));
        assertEquals(2, fetched.size());
        var fetchedVisit = (Visit) fetched.get(0);
        var fetchedPet = (Pet) fetched.get(1);
        assertEquals("Fetch visit", fetchedVisit.description());
        assertEquals("First", fetchedPet.name());
        // The re-fetched graph is hydrated with generated keys throughout.
        assertNotEquals(0, fetchedPet.id());
        assertNotNull(fetchedPet.owner());
        assertNotEquals(0, fetchedPet.owner().id());
        assertEquals(fetchedPet.id(), fetchedVisit.pet().id());
    }

    @Test
    public void testInsertUnsavedParentThroughInlineComponent() {
        var orm = orm();
        // The new city sits inside the owner's inline address component.
        var owner = Owner.builder()
                .firstName("Inline")
                .lastName("City")
                .address(Address.builder().address("1 Inline Way").city(City.builder().name("Graphville").build()).build())
                .build();
        orm.writeSet().insert(List.of(owner));
        var fetched = orm.entity(Owner.class).select().getResultList().stream()
                .filter(candidate -> "Inline".equals(candidate.firstName()))
                .toList();
        assertEquals(1, fetched.size());
        assertNotNull(fetched.getFirst().address().city());
        assertEquals("Graphville", fetched.getFirst().address().city().name());
        assertNotEquals(0, fetched.getFirst().address().city().id());
    }

    @Test
    public void testInsertUnsavedParentThroughWrappedRef() {
        var orm = orm();
        var owner = newOwner("Wrapped", "Ref");
        var pet = PetOwnerRef.builder()
                .name("RefPet")
                .birthDate(LocalDate.of(2021, 5, 5))
                .petType(PetType.builder().id(1).name("dog").build())
                .owner(Ref.of(owner))
                .build();
        orm.writeSet().insert(List.of(pet));
        var fetched = orm.entity(PetOwnerRef.class).select().getResultList().stream()
                .filter(candidate -> "RefPet".equals(candidate.name()))
                .toList();
        assertEquals(1, fetched.size());
        var fetchedOwner = fetched.getFirst().owner().fetch();
        assertEquals("Wrapped", fetchedOwner.firstName());
    }

    @Test
    public void testInsertOrdersKeyedMembersByForeignKey() {
        var orm = orm();
        var ferretType = PetType.builder().id(7).name("ferret").build();
        var pet = PetOwnerRef.builder()
                .name("Ferry")
                .birthDate(LocalDate.of(2020, 6, 6))
                .petType(ferretType)
                .owner(null)
                .build();
        List<String> inserts = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> { if (sql.statement().toUpperCase().startsWith("INSERT")) inserts.add(sql.statement().toLowerCase()); },
                // The pet is passed first, but its natural-key pet type member must be inserted before it.
                () -> { orm.writeSet().insert(List.of(pet, ferretType)); return null; });
        assertEquals(2, inserts.size());
        assertTrue(inserts.get(0).contains("pet_type"));
        var fetched = orm.entity(PetOwnerRef.class).select().getResultList().stream()
                .filter(candidate -> "Ferry".equals(candidate.name()))
                .toList();
        assertEquals(7, fetched.getFirst().petType().id());
    }

    @Test
    public void testInsertIdOnlyRefWithDefaultIdFails() {
        var orm = orm();
        var pet = PetOwnerRef.builder()
                .name("Dangling")
                .birthDate(LocalDate.of(2020, 7, 7))
                .petType(PetType.builder().id(1).name("dog").build())
                .owner(Ref.of(Owner.class, 0))
                .build();
        var exception = assertThrows(PersistenceException.class, () -> orm.writeSet().insert(List.of(pet)));
        assertTrue(exception.getMessage().contains("id-only Ref"));
        assertTrue(exception.getMessage().contains("Ref.of(entity)"));
    }

    @Test
    public void testInsertJunctionPropagatesGeneratedKeyIntoCompositePk() {
        var orm = orm();
        var vet = Vet.builder().firstName("New").lastName("JunctionVet").build();
        var vetSpecialty = new VetSpecialty(
                new VetSpecialtyPK(0, 1),
                vet,
                Specialty.builder().id(1).name("radiology").build());
        List<String> inserts = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> { if (sql.statement().toUpperCase().startsWith("INSERT")) inserts.add(sql.statement()); },
                () -> { orm.writeSet().insert(List.of(vetSpecialty)); return null; });
        // The vet joins via the insertion closure and is written first; its generated key is carried by the
        // junction row's composite primary key.
        assertEquals(2, inserts.size());
        assertTrue(inserts.get(1).contains("vet_specialty"));
        var insertedVets = orm.entity(Vet.class).select().getResultList().stream()
                .filter(candidate -> "JunctionVet".equals(candidate.lastName()))
                .toList();
        assertEquals(1, insertedVets.size());
        var junction = orm.entity(VetSpecialty.class).findById(new VetSpecialtyPK(insertedVets.getFirst().id(), 1));
        assertTrue(junction.isPresent());
        assertEquals("radiology", junction.get().specialty().name());
    }

    @Test
    public void testInsertJunctionRowsSharingUnsavedParent() {
        var orm = orm();
        var vet = Vet.builder().firstName("Shared").lastName("JunctionParent").build();
        var radiology = new VetSpecialty(new VetSpecialtyPK(0, 1), vet,
                Specialty.builder().id(1).name("radiology").build());
        var surgery = new VetSpecialty(new VetSpecialtyPK(0, 2), vet,
                Specialty.builder().id(2).name("surgery").build());
        List<String> inserts = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> { if (sql.statement().toUpperCase().startsWith("INSERT")) inserts.add(sql.statement()); },
                () -> { orm.writeSet().insert(List.of(radiology, surgery)); return null; });
        // The shared vet instance is inserted exactly once; the junction rows form a single batch.
        assertEquals(2, inserts.size());
        var insertedVets = orm.entity(Vet.class).select().getResultList().stream()
                .filter(candidate -> "JunctionParent".equals(candidate.lastName()))
                .toList();
        assertEquals(1, insertedVets.size());
        int vetId = insertedVets.getFirst().id();
        assertTrue(orm.entity(VetSpecialty.class).findById(new VetSpecialtyPK(vetId, 1)).isPresent());
        assertTrue(orm.entity(VetSpecialty.class).findById(new VetSpecialtyPK(vetId, 2)).isPresent());
    }

    @Test
    public void testInsertJunctionRowsWithEqualTransientKeys() {
        var orm = orm();
        // Both junction rows carry the same transient key (0, 1) until their parents' keys are propagated; the
        // write set correlates by instance identity, so each row binds its own parent.
        var first = new VetSpecialty(new VetSpecialtyPK(0, 1),
                Vet.builder().firstName("First").lastName("TransientKey").build(),
                Specialty.builder().id(1).name("radiology").build());
        var second = new VetSpecialty(new VetSpecialtyPK(0, 1),
                Vet.builder().firstName("Second").lastName("TransientKey").build(),
                Specialty.builder().id(1).name("radiology").build());
        orm.writeSet().insert(List.of(first, second));
        var vets = orm.entity(Vet.class).select().getResultList().stream()
                .filter(candidate -> "TransientKey".equals(candidate.lastName()))
                .toList();
        assertEquals(2, vets.size());
        for (var vet : vets) {
            assertTrue(orm.entity(VetSpecialty.class).findById(new VetSpecialtyPK(vet.id(), 1)).isPresent());
        }
    }

    @Test
    public void testInsertAndFetchJunctionReturnsCompleteKey() {
        var orm = orm();
        var vet = Vet.builder().firstName("Fetched").lastName("JunctionVet").build();
        var vetSpecialty = new VetSpecialty(new VetSpecialtyPK(0, 3), vet,
                Specialty.builder().id(3).name("dentistry").build());
        var fetched = orm.writeSet().insertAndFetch(vetSpecialty);
        assertNotEquals(0, fetched.id().vetId());
        assertEquals(fetched.id().vetId(), (int) fetched.vet().id());
        assertEquals("Fetched", fetched.vet().firstName());
        assertEquals(3, fetched.id().specialtyId());
    }

    @Test
    public void testInsertAndFetchJunctionWithEntityTypedPrimaryKey() {
        var orm = orm();
        var owner = newOwner("EntityPk", "Junction");
        var pet = Pet.builder().name("EntityPkJunctionPet").birthDate(LocalDate.of(2024, 4, 4)).type(dogType()).owner(owner).build();
        // The junction's primary key is the owner entity itself, and its join graph reaches the owner table
        // twice (owner and pet.owner); the fetch-back must resolve the propagated ids against the primary-key
        // path rather than by type.
        var fetched = orm.writeSet().insertAndFetch(new OwnerPrimaryPet(owner, pet));
        assertNotEquals(0, (int) fetched.owner().id());
        assertEquals(fetched.owner().id(), fetched.pet().owner().id());
    }

    @DbTable("vet_badge")
    public record VetBadge(
            @PK Integer id,
            @Nonnull String label,
            @Nonnull @FK @Persist(insertable = false, updatable = false) Vet vet
    ) implements Entity<Integer> {}

    @Test
    public void testInsertUnsavedThroughNonInsertableComponentWithoutCarrierFails() {
        var orm = orm();
        // The badge's vet_id column is neither insertable nor carried by a primary key component, so an unsaved
        // vet behind it cannot join the insertion closure.
        var badge = new VetBadge(null, "Unsupported", Vet.builder().firstName("New").lastName("Vet").build());
        var exception = assertThrows(PersistenceException.class, () -> orm.writeSet().insert(List.of(badge)));
        assertTrue(exception.getMessage().contains("not insertable"));
    }

    public interface PetGraphRepository extends EntityRepository<Pet, Integer> {
        default List<Entity<?>> insertPetWithOwner(Owner owner, String petName) {
            var pet = Pet.builder().name(petName).birthDate(LocalDate.of(2024, 3, 3))
                    .type(Ref.of(PetType.class, 1)).owner(owner).build();
            return writeSet().insertAndFetch(List.of(pet));
        }
    }

    @Test
    public void testWriteSetAccessibleFromRepositories() {
        var orm = orm();
        // Directly on an entity repository: the write set is the template's, not scoped to the repository type.
        orm.entity(Pet.class).writeSet().insert(List.of(newOwner("Direct", "RepoAccess")));
        assertEquals(1, orm.entity(Owner.class).select().getResultList().stream()
                .filter(candidate -> "RepoAccess".equals(candidate.lastName()))
                .count());
        // Through a custom repository default method, exercising the repository proxy dispatch.
        var inserted = orm.repository(PetGraphRepository.class)
                .insertPetWithOwner(newOwner("Proxy", "RepoAccess"), "ProxyPet");
        assertEquals(1, inserted.size());
        var fetchedPet = (Pet) inserted.getFirst();
        assertEquals("ProxyPet", fetchedPet.name());
        assertNotNull(fetchedPet.owner());
        assertEquals("Proxy", fetchedPet.owner().firstName());
    }

    @Test
    public void testSingleRootConvenienceVariants() {
        var orm = orm();
        var owner = newOwner("Single", "Root");
        var pet = Pet.builder().name("SingleRootPet").birthDate(LocalDate.of(2024, 7, 7)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 5, 5), "Single root visit", pet);
        // Typed single-root variants: the whole graph goes in, the typed root comes out.
        Visit inserted = orm.writeSet().insertAndFetch(visit);
        assertEquals("Single root visit", inserted.description());
        assertNotEquals(0, inserted.pet().id());
        assertNotNull(inserted.pet().owner());
        Visit renamed = orm.writeSet().updateAndFetch(inserted.toBuilder().description("Renamed visit").build());
        assertEquals("Renamed visit", renamed.description());
        orm.writeSet().remove(renamed);
        assertTrue(orm.entity(Visit.class).findById(renamed.id()).isEmpty());
    }

    @Test
    public void testEqualButDistinctUnsavedParentsProduceSeparateRows() {
        var orm = orm();
        var firstTwin = newOwner("Twin", "Identity");
        var secondTwin = newOwner("Twin", "Identity");
        // The two owners are equal as values but distinct instances: they describe two prospective rows.
        assertEquals(firstTwin, secondTwin);
        var wolfie = Pet.builder().name("TwinPetA").birthDate(LocalDate.of(2024, 5, 5)).type(dogType()).owner(firstTwin).build();
        var rex = Pet.builder().name("TwinPetB").birthDate(LocalDate.of(2024, 6, 6)).type(dogType()).owner(secondTwin).build();
        orm.writeSet().insert(List.of(wolfie, rex));
        var owners = orm.entity(Owner.class).select().getResultList().stream()
                .filter(candidate -> "Identity".equals(candidate.lastName()))
                .toList();
        assertEquals(2, owners.size());
        assertNotEquals(owners.get(0).id(), owners.get(1).id());
    }

    @Test
    public void testUpdateDoesNotWriteReferencedEntities() {
        var orm = orm();
        var pet = orm.entity(Pet.class).getById(1);
        assertNotNull(pet.owner());
        var mutatedOwner = pet.owner().toBuilder().firstName("Mutated").build();
        // The pet's foreign key value (the owner's id) is unchanged, so only the pet row is written.
        orm.writeSet().update(List.of(pet.toBuilder().name("TouchedPet").owner(mutatedOwner).build()));
        assertEquals("TouchedPet", orm.entity(Pet.class).getById(1).name());
        assertNotEquals("Mutated", orm.entity(Owner.class).getById(pet.owner().id()).firstName());
    }

    @Test
    public void testRemoveDoesNotRemoveReferencedEntities() {
        var orm = orm();
        var owner = newOwner("Keep", "Me");
        var pet = Pet.builder().name("Kept").birthDate(LocalDate.of(2020, 2, 2)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 4, 4), "Only removed member", pet);
        var inserted = orm.writeSet().insertAndFetch(List.of(visit));
        // Only the visit is an explicit member; the pet and owner it references must survive.
        orm.writeSet().remove(List.of(inserted.getFirst()));
        assertTrue(orm.entity(Visit.class).findById(((Visit) inserted.getFirst()).id()).isEmpty());
        assertEquals(1, orm.entity(Pet.class).select().getResultList().stream()
                .filter(candidate -> "Kept".equals(candidate.name()))
                .count());
        assertEquals(1, orm.entity(Owner.class).select().getResultList().stream()
                .filter(candidate -> "Keep".equals(candidate.firstName()))
                .count());
    }

    @Test
    public void testUpdateMixedTypes() {
        var orm = orm();
        var owner = orm.entity(Owner.class).getById(1).toBuilder().telephone("0000000000").build();
        var pet = orm.entity(Pet.class).getById(1).toBuilder().name("Renamed").build();
        orm.writeSet().update(List.of(owner, pet));
        assertEquals("0000000000", orm.entity(Owner.class).getById(1).telephone());
        assertEquals("Renamed", orm.entity(Pet.class).getById(1).name());
    }

    @Test
    public void testUpdateAndFetchReflectsDatabaseState() {
        var orm = orm();
        var owner = orm.entity(Owner.class).getById(1);
        var updated = orm.writeSet().updateAndFetch(List.of(owner.toBuilder().telephone("1111111111").build()));
        assertEquals(1, updated.size());
        var fetchedOwner = (Owner) updated.getFirst();
        assertEquals("1111111111", fetchedOwner.telephone());
        // The version column is bumped by the update and reflected by the fetch.
        assertEquals(owner.version() + 1, fetchedOwner.version());
    }

    @Test
    public void testUpdateUnsavedFails() {
        var orm = orm();
        var exception = assertThrows(PersistenceException.class,
                () -> orm.writeSet().update(List.of(newOwner("Not", "Saved"))));
        assertTrue(exception.getMessage().contains("unsaved"));
    }

    @Test
    public void testUpsertInsertsClosureMembersBeforeDelegating() {
        // The default H2 implementation does not support upsert; per-repository upsert throws. The write set must
        // surface that same exception for the passed members, but only after the unsaved closure members have been
        // resolved, showing the delegation reaches the per-type upsert unchanged.
        var orm = orm();
        var newOwner = newOwner("Upsert", "Fresh");
        var pet = Pet.builder().name("Upserted").birthDate(LocalDate.of(2019, 9, 9)).type(dogType()).owner(newOwner).build();
        var exception = assertThrows(PersistenceException.class, () -> orm.writeSet().upsert(List.of(pet)));
        assertTrue(exception.getMessage().contains("Upsert is not available"), exception.getMessage());
        // The closure member was inserted before the upsert of the passed member failed.
        var owners = orm.entity(Owner.class).select().getResultList().stream()
                .filter(candidate -> "Upsert".equals(candidate.firstName()))
                .toList();
        assertEquals(1, owners.size());
    }

    @Test
    public void testRemoveChildrenBeforeParents() {
        var orm = orm();
        var owner = newOwner("Remove", "Me");
        var pet = Pet.builder().name("Removable").birthDate(LocalDate.of(2018, 8, 8)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 3, 3), "Remove visit", pet);
        var inserted = orm.writeSet().insertAndFetch(List.of(owner, pet, visit));
        var insertedOwner = (Owner) inserted.get(0);
        var insertedPet = (Pet) inserted.get(1);
        var insertedVisit = (Visit) inserted.get(2);
        // Parents first in the argument list; the write set must reorder (H2 enforces the FK constraints).
        orm.writeSet().remove(List.of(insertedOwner, insertedPet, insertedVisit));
        assertTrue(orm.entity(Owner.class).findById(insertedOwner.id()).isEmpty());
        assertTrue(orm.entity(Pet.class).findById(insertedPet.id()).isEmpty());
        assertTrue(orm.entity(Visit.class).findById(insertedVisit.id()).isEmpty());
    }

    @Test
    public void testRemoveUnsavedFails() {
        var orm = orm();
        var exception = assertThrows(PersistenceException.class,
                () -> orm.writeSet().remove(List.of(newOwner("Never", "Persisted"))));
        assertTrue(exception.getMessage().contains("unsaved"));
    }

    @Test
    public void testVarargActionsAcceptEnumeratedEntities() {
        var orm = orm();
        var owner = newOwner("Vararg", "Actions");
        var pet = Pet.builder().name("VarargPet").birthDate(LocalDate.of(2024, 4, 4)).type(dogType()).owner(owner).build();
        var visit = new Visit(LocalDate.of(2026, 5, 5), "Vararg visit", pet);
        // Multi-argument calls resolve to the vararg overloads; single-argument calls keep resolving to the
        // typed single-root variants (see testSingleRootConvenienceVariants).
        var inserted = orm.writeSet().insertAndFetch(pet, visit);
        assertEquals(2, inserted.size());
        var insertedPet = (Pet) inserted.get(0);
        var insertedVisit = (Visit) inserted.get(1);
        assertNotEquals(0, insertedPet.owner().id());
        var renamedPet = insertedPet.toBuilder().name("VarargRenamed").build();
        orm.writeSet().update(renamedPet, insertedVisit);
        assertEquals("VarargRenamed", orm.entity(Pet.class).getById(insertedPet.id()).name());
        orm.writeSet().remove(insertedPet.owner(), renamedPet, insertedVisit);
        assertTrue(orm.entity(Owner.class).findById(insertedPet.owner().id()).isEmpty());
        assertTrue(orm.entity(Pet.class).findById(insertedPet.id()).isEmpty());
        assertTrue(orm.entity(Visit.class).findById(insertedVisit.id()).isEmpty());
    }

    @Test
    public void testEmptyVarargCallsAreNoOps() {
        var orm = orm();
        orm.writeSet().insert();
        orm.writeSet().update();
        orm.writeSet().upsert();
        orm.writeSet().remove();
        assertTrue(orm.writeSet().insertAndFetch().isEmpty());
        assertTrue(orm.writeSet().updateAndFetch().isEmpty());
        assertTrue(orm.writeSet().upsertAndFetch().isEmpty());
    }

}
