package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import st.orm.template.Sql;
import st.orm.template.SqlTemplate;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Templates.ORM;
import static st.orm.template.Operator.BETWEEN;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.GREATER_THAN;
import static st.orm.template.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.template.Operator.IN;
import static st.orm.template.Operator.IS_NULL;
import static st.orm.template.impl.SqlTemplateImpl.DefaultJoinType.INNER;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RepositoryPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        assertEquals(10, ORM(dataSource).repository(Owner.class).selectCount().getSingleResult());
    }

    @Test
    public void testCount() {
        assertEquals(10, ORM(dataSource).repository(Owner.class).count());
    }

    @Test
    public void testResultCount() {
        assertEquals(10, ORM(dataSource).repository(Owner.class).select().getResultCount());
    }

    @Test
    public void testSelectByFk() {
        assertEquals(1, ORM(dataSource).repository(Pet.class).select().where(Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByFkNested() {
        assertEquals(2, ORM(dataSource).repository(Visit.class).select().where(Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        assertEquals(1, ORM(dataSource).repository(Visit.class).select().where("visitDate", EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        assertEquals(13, ORM(dataSource).repository(Visit.class).select().where("visitDate", GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        assertEquals(14, ORM(dataSource).repository(Visit.class).select().where("visitDate", GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnBetween() {
        assertEquals(10, ORM(dataSource).repository(Visit.class).select().where("visitDate", BETWEEN, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 9)).getResultCount());
    }

    @Test
    public void testSelectByColumnIsNull() {
        assertEquals(1, ORM(dataSource).repository(Pet.class).select().where("owner", IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByNestedColumn() {
        assertEquals(2, ORM(dataSource).repository(Visit.class).select().where("pet.name", EQUALS, "Leo").getResultCount());
    }

    @Test
    public void testSelectByDeeperNestedColumn() {
        assertEquals(2, ORM(dataSource).repository(Visit.class).select().where("pet.owner.firstName", EQUALS, "Betty").getResultCount());
    }

    @Test
    public void testSelectByUnknownColumn() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).repository(Visit.class).select().where("pet.names", EQUALS,"Leo").getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectCountCustomClass() {
        record Count(Owner owner, int value) {}
        var query = ORM(dataSource).query(RAW."SELECT \{Owner.class}, COUNT(*) FROM \{Pet.class} GROUP BY \{Owner.class}.id");
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
        var list = repository.select().getResultList();
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
        try (var stream1 = repository.selectAll()) {
            var list1 = stream1.toList();
            repository.update(list1);
            try (var stream2 = repository.selectAll()) {
                var list2 = stream2.toList();
                assertEquals(list1, list2);
            }
        }
    }

    @Test
    public void testSelectList() {
        var repository = ORM(dataSource).repository(Vet.class);
        var list = repository.select().getResultList();
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
        try (var stream = ORM.repository(PetWithLazyNullableOwner.class).selectAll()) {
            var pet = stream.filter(p -> p.name().equals("Sly")).findFirst().orElseThrow();
            assertTrue(pet.owner().isNull());
            assertNull(pet.owner().fetch());
        }
    }

    @Test
    public void testSelectLazyInnerJoin() {
        // Lazy elements are not joined by default. Test whether join works.
        var owners = ORM(dataSource)
                .selectFrom(PetWithLazyNullableOwner.class, Owner.class, RAW."DISTINCT \{Owner.class}")
                .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                .getResultList();
        assertEquals(10, owners.size());
    }

    @Test
    public void testSelectWithWrapper() {
        record Wrapper(Pet pet) {}
        var pets = ORM(dataSource)
                .selectFrom(Pet.class, Wrapper.class)
                .getResultList();
        assertEquals(13, pets.size());
    }

    @Builder(toBuilder = true)
    @Name("visit")
    public record VisitWithTwoPets(
            @PK Integer id,
            @Nonnull @Name("visit_date") LocalDate visitDate,
            @Nullable String description,
            @FK @Name("pet_id") @Qualifier("mom") PetLazyOwner pet1,
            @FK @Name("pet_id") @Qualifier("dad") PetLazyOwner pet2
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
    public void testSelectWithTwoPetsWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).selectFrom(Owner.class).getResultList().getFirst();
            ORM(dataSource).repository(VisitWithTwoPets.class).select().where(owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithoutPathTemplate() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).repository(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).repository(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.expression(RAW."\{PetLazyOwner.class}.owner_id = \{owner.id()}")).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithPath() {
        var owner = ORM(dataSource).repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var list = ORM(dataSource).repository(VisitWithTwoPets.class).select().where("pet1.owner", EQUALS, owner).getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testSelectWithTwoPetsWithPathTemplate() {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM.repository(VisitWithTwoPets.class)
                .select()
                .where(it -> it.expression(RAW."\{ORM.a(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsWithMultipleParameters() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPets.class).select().where(it -> it.filter("pet1.owner", EQUALS, owner).or(it.filter("pet2.owner", EQUALS, owner))).getResultList(), sql::setPlain);
        assertEquals(2, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }


    @Test
    public void testSelectWithTwoPetsWithMultipleParametersTemplate() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPets.class)
                .select()
                .where(it -> it.expression(RAW."\{ORM.a(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()} OR \{ORM.a(PetLazyOwner.class, "pet2")}.owner_id = \{owner.id()}")).getResultList(), sql::setPlain);
        assertEquals(2, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
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
    public void testSelectWithTwoPetsOneLazyWithoutPath() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).select().where(owner).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithoutPathTemplate() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class)
                .select()
                .where(it -> it.expression(RAW."\{PetLazyOwner.class}.owner_id = \{owner.id()}")).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithPath() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).select().where("pet1.owner", EQUALS, owner).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithPathTemplate() throws Exception {
        var ORM = ORM(dataSource);
        var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class)
                .select()
                .where(it -> it.expression(RAW."\{ORM.a(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()}")).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM.repository(VisitWithTwoPetsOneLazy.class).select().where(pet).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithoutPathTemplate() {
        // Note that this test is not comparable to the previous test because of the re-use of pet_id.
        var ORM = ORM(dataSource);
        var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM.repository(VisitWithTwoPetsOneLazy.class)
                .select()
                .where(it -> it.expression(RAW."\{PetLazyOwner.class}.id = \{pet.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithPath() throws Exception {
        var ORM = ORM(dataSource);
        var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).select().where("pet1", EQUALS, pet).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithPathTemplate() throws Exception {
        var ORM = ORM(dataSource);
        var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class)
                .select()
                .where(it -> it.expression(RAW."\{ORM.a(PetLazyOwner.class, "pet1")}.id = \{pet.id()}")).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyOtherPetWithPath() throws Exception {
        var ORM = ORM(dataSource);
        var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        var visits = SqlTemplate.aroundInvoke(() -> ORM.repository(VisitWithTwoPetsOneLazy.class).select().where("pet2", EQUALS, pet).getResultList(), sql::setPlain);
        assertEquals(1, sql.getPlain().parameters().size());
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyOtherPetWithPathTemplate() {
        // Note that this test is not comparable to the previous test because no join should exist for pet2 as it's
        // lazy, and therefore pet.id will be used.
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var pet = ORM.repository(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM.repository(VisitWithTwoPetsOneLazy.class)
                    .select()
                    .where(it -> it.expression(RAW."\{ORM.a(PetLazyOwner.class, "pet2")}.id = \{pet.id()}")).getResultList();
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
    public void testSelectWithTwoLazyPetsWithoutPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM.repository(VisitWithTwoLazyPets.class).select().where(owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsWithoutPathTemplate() {
        // Will default to pet.id as it won't find any aliases for PetLazyOwner, therefore this will be a SQL syntax error.
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM.repository(VisitWithTwoLazyPets.class)
                    .select()
                    .where(it -> it.expression(RAW."\{PetLazyOwner.class}.id = \{owner.id()}")).getResultList();
        });
        assertInstanceOf(SQLSyntaxErrorException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsWithPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var owner = ORM.repository(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM.repository(VisitWithTwoLazyPets.class).select().where("pet1.owner", EQUALS, owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePath() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Owner.class).select().where("address.city", EQUALS, "Madison").getResultList();
        assertEquals(4, list.size());
    }

    @Test
    public void testSelectWithInlinePathEqualsMultiArgument() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).repository(Owner.class).select().where("address.city", EQUALS, "Madison", "Monona").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePathInMultiArgument() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Owner.class).select().where("address.city", IN, "Madison", "Monona").getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testSelectWhere() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(Pet.class)
                .select()
                .where(owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhere() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(PetWithLazyNullableOwner.class)
                .select()
                .where(owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhereWithJoin() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            Owner owner = Owner.builder().id(1).build();
            ORM.repository(PetWithLazyNullableOwner.class)
                    .select()
                    .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                    .where(owner)
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectLazyWhereWithJoinAndPath() {
        var ORM = ORM(dataSource);
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM.repository(PetWithLazyNullableOwner.class)
                .select()
                .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                .where("owner", EQUALS, owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectNullableOwner() {
        // Lazy elements are not joined by default. Test whether join works.
        var ORM = ORM(dataSource);
        var owners = ORM
                .selectFrom(Pet.class, Owner.class, RAW."\{Owner.class}")
                .getResultList();
        assertEquals(12, owners.size());
    }

    @Test
    public void testCustomRepo1() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById1(1));
    }

    @Test
    public void testCustomRepo2() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById2(1));
    }

    @Test
    public void testCustomRepo3() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById3(1));
    }

    @Test
    public void testCustomRepo4() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        assertEquals(1, repo.findByOwnerFirstName("Betty").size());
    }

    @Test
    public void testCustomRepo5() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        assertEquals(4, repo.findByOwnerCity("Madison").size());
    }

    @Test
    public void testPetVisitCount() {
        var repo = ORM(dataSource).repositoryProxy(PetRepository.class);
        assertEquals(8, repo.petVisitCount().size());
    }

    @Test
    public void delete() {
        var R = ORM(dataSource).repository(Visit.class);
        R.delete(Visit.builder().id(1).build());
        assertEquals(13, R.select().getResultCount());
    }

    @Test
    public void deleteAll() {
        var R = ORM(dataSource).repository(Visit.class);
        R.deleteAll();
        assertEquals(0, R.select().getResultCount());
    }

    @Test
    public void deleteBatch() {
        var R = ORM(dataSource).repository(Visit.class);
        try (var stream = R.selectAll()) {
            R.delete(stream);
        }
        assertEquals(0, R.count());
    }

    @Test
    public void testBuilder() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class)
                .select()
                .append(RAW."WHERE \{Pet.builder().id(1).build()}")
                .getResultList();
        assertEquals(1, list.size());
    }

    @Test
    public void testBuilderWithJoin() {
        var orm = ORM(dataSource);
        var list = orm
                .repository(Pet.class)
                .select()
                .innerJoin(Visit.class).on(RAW."\{orm.a(Pet.class)}.id = \{Visit.class}.pet_id")
                .append(RAW."WHERE \{orm.a(Visit.class)}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
        assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var list = ORM(dataSource)
                .repository(Pet.class)
                .select()
                .innerJoin(Visit.class).on(Pet.class)
                .where(Visit.builder().id(1).build())
                .getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoAndCustomJoin() {
        var orm = ORM(dataSource);
        var list = orm
                .repository(Pet.class)
                .select()
                .innerJoin(Visit.class).on(Pet.class)
                .innerJoin("x", RAW."SELECT * FROM \{orm.t(Pet.class)}").on(RAW."\{orm.a(Pet.class)}.id = x.id")    // Join just for the sake of testing multiple joins.
                .where(Visit.builder().id(1).build())
                .getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
                    .repository(Pet.class)
                    .select()
                    .innerJoin(Visit.class).on(Pet.class)
                    .where(Vet.builder().id(1).build())
                    .getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithSelectTemplate() {
        record Result(int petId, int visitCount) {}
        var ORM = ORM(dataSource);
        var list = ORM
                .selectFrom(Pet.class, Result.class, RAW."\{ORM.a(Pet.class)}.id, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .append(RAW."GROUP BY \{ORM.a(Pet.class)}.id")
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithNonRecordSelectTemplate() {
        var count = ORM(dataSource)
                .selectFrom(Pet.class, Integer.class, RAW."COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .getSingleResult();
        assertEquals(14, count);
    }

    @Test
    public void testBuilderWithCustomJoin() {
        record Result(int petId, int visitCount) {}
        var ORM = ORM(dataSource);
        var list = ORM
                .selectFrom(Pet.class, Result.class, RAW."\{ORM.a(Pet.class)}.id, COUNT(*)")
                .join(INNER, "x", RAW."SELECT * FROM \{ORM.t(Visit.class, "a")} WHERE \{ORM.a(Visit.class)}.id > \{-1}").on(RAW."\{Pet.class}.id = x.pet_id")
                .append(RAW."GROUP BY \{ORM.a(Pet.class)}.id")
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testWithArg() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class).select().append(it -> STR."WHERE \{it.invoke(Pet.class)}.id = 7").getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
        ORM.repository(Pet.class).count(list.stream().map(Pet::id));
    }

    @Test
    public void testWithTwoArgs() {
        var ORM = ORM(dataSource);
        var list = ORM.repository(Pet.class).select().append(it -> STR."WHERE \{it.invoke(Pet.class)}.id = 7 OR \{it.invoke(Pet.class)}.id = 8").getResultList();
        assertEquals(2, list.size());
        assertEquals(7, list.getFirst().id());
        assertEquals(8, list.getLast().id());
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
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        Visit updatedVisit = repo.updateAndFetch(modifiedVisit);
        repo.update(updatedVisit);
    }

    @Test
    public void updateVisitWrongTimestampVersion() {
        var ORM = ORM(dataSource);
        var repo = ORM.repository(Visit.class);
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        repo.update(modifiedVisit);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedVisit));
    }
}