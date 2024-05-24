package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import lombok.Builder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.VetSpecialtyPK;
import st.orm.model.Visit;
import st.orm.repository.Entity;
import st.orm.repository.PetRepository;
import st.orm.template.PreparedStatementTemplate;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.SqlTemplateImpl;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Templates.ORM;
import static st.orm.template.SqlTemplate.AliasResolveStrategy.ALL;
import static st.orm.template.SqlTemplate.AliasResolveStrategy.FAIL;
import static st.orm.template.SqlTemplate.AliasResolveStrategy.FIRST;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RepositoryPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        var ORM = ORM(dataSource);
        Assertions.assertEquals(10, ORM.repository(Owner.class).selectAll().count());
    }

    @Test
    public void testSelectCount() {
        var ORM = ORM(dataSource);
        assertEquals(10, ORM.repository(Owner.class).count());
    }

    @Test
    public void testSelectByFk() {
        var ORM = ORM(dataSource);
        Assertions.assertEquals(1, ORM.repository(Pet.class).selectMatches(Owner.builder().id(1).build()).count());
    }

    @Test
    public void testSelectCountByFk() {
        var ORM = ORM(dataSource);
        assertEquals(1, ORM.repository(Pet.class).countMatches(Owner.builder().id(1).build()));
    }

    @Test
    public void testSelectByFkNested() {
        var ORM = ORM(dataSource);
        Assertions.assertEquals(2, ORM.repository(Visit.class).selectMatches(Owner.builder().id(1).build()).count());
    }

    @Test
    public void testSelectCountByFkNested() {
        var ORM = ORM(dataSource);
        assertEquals(2, ORM.repository(Visit.class).countMatches(Owner.builder().id(1).build()));
    }

    @Test
    public void testSelectCountCustomClass() {
        record Count(Owner owner, int value) {}
        var ORM = ORM(dataSource);
        var query = ORM."SELECT \{Owner.class}, COUNT(*) FROM \{Pet.class} GROUP BY \{Owner.class}.id";
        var result = query.getResultList(Count.class);
        assertEquals(10, result.size());
        assertEquals(12, result.stream().mapToInt(Count::value).sum());
    }

    @Test
    public void testInsert() {
        var repository = ORM(dataSource).repository(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        repository.insert(List.of(vet1, vet2));
        var list = repository.selectAll().toList();
        assertEquals(8, list.size());
        assertEquals("Scarlett", list.getLast().firstName());
    }

    @Test
    public void testInsertReturningIds() {
        var repository = ORM(dataSource).repository(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        var ids = repository.insertAndFetchIds(List.of(vet1, vet2));
        assertEquals(List.of(7, 8), ids);
    }

    @Test
    public void testInsertReturningIdsCompoundPk() {
        try {
            var repository = ORM(dataSource).repository(VetSpecialty.class);
            VetSpecialty vetSpecialty = VetSpecialty.builder().id(new VetSpecialtyPK(1, 1)).build();
            var id = repository.insertAndFetchId(vetSpecialty);
            assertEquals(1, id.vetId());
            assertEquals(1, id.specialtyId());
        } catch (PersistenceException _) {
            // May happen in case of Mysql/MariaDB as they only support auto increment columns.
        }
    }

    @Test
    public void testUpdateList() {
        var repository = ORM(dataSource).repository(Vet.class);
        var list = repository.selectAll().toList();
        repository.update(list);
        var list2 = repository.selectAll().toList();
        assertEquals(list, list2);
    }

    @Test
    public void testSelectList() {
        var repository = ORM(dataSource).repository(Vet.class);
        var list = repository.selectAll().toList();
        var list2 = repository.select(list.stream().map(Vet::id).toList());
        assertEquals(list, list2);
    }

    @Test
    public void testSelectLazy() {
        var ORM = ORM(dataSource);
        var visit = ORM.repository(VisitWithLazyNullablePet.class).select(1);
        var pet = visit.pet().fetch();
        assertFalse(visit.pet().isNull());
        assertEquals("Jean", pet.owner().firstName());
        assertSame(pet, visit.pet().fetch());
    }

    @Test
    void testInsertPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(Visit.class);
            Visit visit = new Visit(1, LocalDate.now(), "test", null, Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(Visit.class);
            var visit = repository.select(1);
            repository.update(new Visit(visit.id(), visit.visitDate(), visit.description(), null, Instant.now()));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithLazyNonnullPet(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK @Name("pet_id") Lazy<Pet> pet
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertLazyPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(VisitWithLazyNonnullPet.class);
            VisitWithLazyNonnullPet visit = new VisitWithLazyNonnullPet(1, LocalDate.now(), "test", Lazy.ofNull());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdateLazyPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(VisitWithLazyNonnullPet.class);
            var visit = repository.select(1);
            repository.update(new VisitWithLazyNonnullPet(visit.id(), visit.visitDate(), visit.description(), null));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithLazyNullablePet(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @Name("pet_id") Lazy<Pet> pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertLazyNullablePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(VisitWithLazyNullablePet.class);
            VisitWithLazyNullablePet visit = new VisitWithLazyNullablePet(1, LocalDate.now(), "test", Lazy.ofNull(), Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testUpdateLazyNullablePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).repository(VisitWithLazyNullablePet.class);
            var visit = repository.select(1);
            repository.update(visit.toBuilder().pet(Lazy.ofNull()).build());
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testInsertLazyNullablePetWithNonnull() {
        var repository = ORM(dataSource).repository(VisitWithLazyNullablePet.class);
        VisitWithLazyNullablePet visit = new VisitWithLazyNullablePet(1, LocalDate.now(), "test", Lazy.of(Pet.builder().id(1).build()), Instant.now());
        var id = repository.insertAndFetchId(visit);
        var visitFromDb = repository.select(id);
        assertEquals(id, visitFromDb.id());
        assertFalse(visitFromDb.pet().isNull());
        assertEquals(1, visitFromDb.pet().fetch().id());
    }

    @Name("pet")
    public record PetWithLazyNullableOwner(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Name("type_id") @Persist(updatable = false) PetType petType,
            @FK @Name("owner_id") Lazy<Owner> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectLazyNullOwner() {
        var ORM = ORM(dataSource);
        var pet = ORM.repository(PetWithLazyNullableOwner.class).selectAll().filter(p -> p.name().equals("Sly")).findFirst().orElseThrow();
        assertTrue(pet.owner().isNull());
        assertNull(pet.owner().fetch());
    }

    @Test
    public void testSelectLazyInnerJoin() {
        // Lazy elements are not joined by default. Test whether join works.
        var ORM = ORM(dataSource);
        var owners = ORM.repository(PetWithLazyNullableOwner.class)
                .selectTemplate(Owner.class)."DISTINCT \{Owner.class}"
                .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                .toList();
        assertEquals(10, owners.size());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithTwoPets(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @FK @Name("pet_id") PetLazyOwner pet1,
            @FK @Name("pet_id") PetLazyOwner pet2
    ) implements Entity<Integer> {
    }

    @Builder(toBuilder = true)
    @Name("pet")
    public record PetLazyOwner(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Name("type_id") @Persist(updatable = false) PetType petType,
            @FK @Name("owner_id") Lazy<Owner> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectWithTwoPetsAllStrategy() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(ALL).toORM();
        var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPets.class).selectMatches(owner).toList(), sql::setPlain);
        assertEquals(2, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsFirstStrategy() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FIRST).toORM();
        var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPets.class).selectMatches(owner).toList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsFailStrategy() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FAIL).toORM();
            var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
            ORM.repository(VisitWithTwoPets.class).selectMatches(owner).toList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithTwoPetsOneLazy(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @Name("pet_id") PetLazyOwner pet1,
            @Nullable @FK @Name("pet_id") Lazy<PetLazyOwner> pet2
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectWithTwoPetsOneLazyAllStrategy() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(ALL).toORM();
        var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(owner).toList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyFirstStrategy() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FIRST).toORM();
        var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(owner).toList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyFailStrategy() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FAIL).toORM();
        var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(owner).toList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyAllStrategyPet() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(ALL).toORM();
        var pet = ORM.repository(PetLazyOwner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(pet).toList(), sql::setPlain);
        assertEquals(2, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyFirstStrategyPet() throws Exception {
        var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FIRST).toORM();
        var pet = ORM.repository(PetLazyOwner.class).selectAll().toList().getFirst();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(pet).toList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyFailStrategyPet() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FAIL).toORM();
            var pet = ORM.repository(PetLazyOwner.class).selectAll().toList().getFirst();
            ORM.repository(VisitWithTwoPetsOneLazy.class).selectMatches(pet).toList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithTwoLazyPets(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @Name("pet_id") Lazy<PetLazyOwner> pet1,
            @Nullable @FK @Name("pet_id") Lazy<PetLazyOwner> pet2
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectWithTwoLazyPetsAllStrategy() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(ALL).toORM();
            var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
            ORM.repository(VisitWithTwoLazyPets.class).selectMatches(owner).toList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsFirstStrategy() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FIRST).toORM();
            var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
            ORM.repository(VisitWithTwoLazyPets.class).selectMatches(owner).toList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsFailStrategy() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = PreparedStatementTemplate.of(dataSource).withAliasResolveStrategy(FAIL).toORM();
            var owner = ORM.repository(Owner.class).selectAll().toList().getFirst();
            ORM.repository(VisitWithTwoLazyPets.class).selectMatches(owner).toList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWhere() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(Pet.class)
                .where(owner)
                .toList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhere() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(PetWithLazyNullableOwner.class)
                .where(owner)
                .toList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhereWithJoin() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(PetWithLazyNullableOwner.class)
                .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                .where(owner)
                .toList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectNullableOwner() {
        // Lazy elements are not joined by default. Test whether join works.
        var ORM = ORM(dataSource);
        var owners = ORM.repository(Pet.class)
                .selectTemplate(Owner.class)."\{Owner.class}"
                .toList();
        assertEquals(12, owners.size());
    }

    @Test
    public void testCustomRepo1() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.findById1(1));
        System.out.println(repo.select(1));
    }

    @Test
    public void testCustomRepo2() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.findById2(1));
        System.out.println(repo.select(1));
    }

    @Test
    public void testCustomRepo3() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.findById3(1));
        System.out.println(repo.select(1));
    }

    @Test
    public void testCustomRepo4() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.findByOwnerFirstName("Betty").toList());
    }

    @Test
    public void testCustomRepo5() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.findByOwnerCity("Madison").toList());
    }

    @Test
    public void testPetVisitCount() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        System.out.println(repo.petVisitCount().toList());
    }

    @Test
    public void delete() {
        var R = ORM(dataSource).repository(Visit.class);
        R.delete(Visit.builder().id(1).build());
        assertEquals(13, R.selectAll().count());
    }

    @Test
    public void deleteAll() {
        var R = ORM(dataSource).repository(Visit.class);
        R.deleteAll();
        assertEquals(0, R.selectAll().count());
    }

    @Test
    public void deleteBatch() {
        var R = ORM(dataSource).repository(Visit.class);
        R.delete(R.selectAll());
        assertEquals(0, R.selectAll().count());
    }

    @Test
    public void testBuilder() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                ."WHERE \{Pet.builder().id(1).build()}"
                .toList();
        assertEquals(1, list.size());
    }

    @Test
    public void testBuilderWithJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                .innerJoin(Visit.class).on()."\{ORM.a(Pet.class)}.id = \{Visit.class}.pet_id"
                ."WHERE \{ORM.a(Visit.class)}.visit_date = \{LocalDate.of(2023, 1, 8)}"
                .toList();
        assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                .innerJoin(Visit.class).on(Pet.class)
                .where(Visit.builder().id(1).build())
                .stream()
                .toList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            ORM.repository(Pet.class)
                    .innerJoin(Visit.class).on(Pet.class)
                    .where(Vet.builder().id(1).build())
                    .stream()
                    .count();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithSelectTemplate() {
        record Result(int petId, int visitCount) {}
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                .selectTemplate(Result.class)."\{ORM.a(Pet.class)}.id, COUNT(*)"
                .innerJoin(Visit.class).on(Pet.class)
                ."GROUP BY \{ORM.a(Pet.class)}.id"
                .toList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithNonRecordSelectTemplate() {
        var ORM = ORM(dataSource);
        var count = ORM.singleResult(ORM.repository(Pet.class)
                .selectTemplate(Integer.class)."COUNT(*)"
                .innerJoin(Visit.class).on(Pet.class)
                .stream());
        assertEquals(14, count);
    }

    @Test
    public void testBuilderWithCustomJoin() {
        record Result(int petId, int visitCount) {}
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                .selectTemplate(Result.class)."\{ORM.a(Pet.class)}.id, COUNT(*)"
                .join(SqlTemplateImpl.DefaultJoinType.INNER, "x")."SELECT * FROM \{ORM.t(Visit.class, "a")} WHERE \{ORM.a(Visit.class)}.id > \{-1}".on()."\{Pet.class}.id = x.pet_id"
                ."GROUP BY \{ORM.a(Pet.class)}.id"
                .toList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testWithArg() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class).withTemplate(it -> STR."WHERE \{it.arg(Pet.class)}.id = 7").toList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testWithTwoArgs() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class).withTemplate(it -> STR."WHERE \{it.arg(Pet.class)}.id = 7 OR \{it.arg(Pet.class)}.id = 8").toList();
        assertEquals(2, list.size());
        assertEquals(7, list.getFirst().id());
        assertEquals(8, list.getLast().id());
    }

    @Test
    public void testWithInvalidPlaceholder() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            ORM.repository(Pet.class).withTemplate(it -> STR."WHERE %s = 1 AND \{it.arg(Pet.class)}.id = 7").build();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void updateOwnerIntegerVersion() {
        var ORM = ORM(dataSource);
        var repo = ORM.repository(Owner.class);
        Owner owner = repo.select(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        Owner updatedOwner = repo.updateAndFetch(modifiedOwner);
        repo.update(updatedOwner);
    }

    @Test
    public void updateOwnerWrongIntegerVersion() {
        var ORM = ORM(dataSource);
        var repo = ORM.repository(Owner.class);
        Owner owner = repo.select(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        repo.update(modifiedOwner);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedOwner));
    }

    @Test
    public void updateVisitTimestampVersion() {
        var ORM = ORM(dataSource);
        var repo = ORM.repository(Visit.class);
        Visit visit = repo.selectAll().toList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        Visit updatedVisit = repo.updateAndFetch(modifiedVisit);
        repo.update(updatedVisit);
    }

    @Test
    public void updateVisitWrongTimestampVersion() {
        var ORM = ORM(dataSource);
        var repo = ORM.repository(Visit.class);
        Visit visit = repo.selectAll().toList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        repo.update(modifiedVisit);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedVisit));
    }
}