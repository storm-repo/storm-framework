package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
import st.orm.template.ResolveScope;
import st.orm.template.Sql;
import st.orm.template.SqlInterceptor;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
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
import static st.orm.Templates.alias;
import static st.orm.Templates.table;
import static st.orm.template.Operator.BETWEEN;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.GREATER_THAN;
import static st.orm.template.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.template.Operator.IN;
import static st.orm.template.Operator.IS_NULL;
import static st.orm.template.ResolveScope.OUTER;
import static st.orm.template.SqlInterceptor.intercept;
import static st.orm.template.TemplateFunction.template;
import static st.orm.template.impl.SqlTemplateImpl.DefaultJoinType.INNER;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RepositoryPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        assertEquals(10, ORM(dataSource).entity(Owner.class).selectCount().getSingleResult());
    }

    @Test
    public void testCount() {
        assertEquals(10, ORM(dataSource).entity(Owner.class).count());
    }

    @Test
    public void testResultCount() {
        assertEquals(10, ORM(dataSource).entity(Owner.class).select().getResultCount());
    }

    @Test
    public void testSelectByFk() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByFkNested() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where(Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        assertEquals(1, ORM(dataSource).entity(Visit.class).select().where("visitDate", EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        assertEquals(13, ORM(dataSource).entity(Visit.class).select().where("visitDate", GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        assertEquals(14, ORM(dataSource).entity(Visit.class).select().where("visitDate", GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnBetween() {
        assertEquals(10, ORM(dataSource).entity(Visit.class).select().where("visitDate", BETWEEN, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 9)).getResultCount());
    }

    @Test
    public void testSelectByColumnIsNull() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where("owner", IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByColumnEqualsNull() {
        // Note that id = NULL results in false.
        assertEquals(0, ORM(dataSource).entity(Pet.class).select().where("owner", EQUALS, (Object) null).getResultCount());
    }

    @Test
    public void testSelectByNestedColumn() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where("pet.name", EQUALS, "Leo").getResultCount());
    }

    @Test
    public void testSelectByDeeperNestedColumn() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where("pet.owner.firstName", EQUALS, "Betty").getResultCount());
    }

    @Test
    public void testSelectByUnknownColumn() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Visit.class).select().where("pet.names", EQUALS,"Leo").getResultCount();
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
        var repository = ORM(dataSource).entity(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        repository.insert(List.of(vet1, vet2));
        var list = repository.select().getResultList();
        assertEquals(8, list.size());
        assertEquals("Scarlett", list.getLast().firstName());
    }

    @Test
    public void testInsertReturningIds() {
        var repository = ORM(dataSource).entity(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        var ids = repository.insertAndFetchIds(List.of(vet1, vet2));
        assertEquals(List.of(7, 8), ids);
    }

    @Test
    public void testInsertReturningIdsCompoundPk() {
        try {
            var repository = ORM(dataSource).entity(VetSpecialty.class);
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
        var repository = ORM(dataSource).entity(Vet.class);
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
        var repository = ORM(dataSource).entity(Vet.class);
        var list = repository.select().getResultList();
        var list2 = repository.select(list.stream().map(Vet::id).toList());
        assertEquals(list, list2);
    }

    @Test
    public void testSelectLazy() {
        var visit = ORM(dataSource).entity(VisitWithLazyNullablePet.class).select(1);
        var pet = visit.pet().fetch();
        assertFalse(visit.pet().isNull());
        assertEquals("Jean", pet.owner().firstName());
        assertSame(pet, visit.pet().fetch());
    }

    @Test
    void testInsertPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(Visit.class);
            Visit visit = new Visit(1, LocalDate.now(), "test", null, Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(Visit.class);
            var visit = repository.select(1);
            repository.update(new Visit(visit.id(), visit.visitDate(), visit.description(), null, Instant.now()));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @DbName("visit")
    public record VisitWithLazyNonnullPet(
            @PK Integer id,
            @Nonnull @DbName("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK @DbName("pet_id") Lazy<Pet, Integer> pet
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertLazyPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithLazyNonnullPet.class);
            VisitWithLazyNonnullPet visit = new VisitWithLazyNonnullPet(1, LocalDate.now(), "test", Lazy.ofNull());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdateLazyPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithLazyNonnullPet.class);
            var visit = repository.select(1);
            repository.update(new VisitWithLazyNonnullPet(visit.id(), visit.visitDate(), visit.description(), null));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @DbName("visit")
    public record VisitWithLazyNullablePet(
            @PK Integer id,
            @Nonnull @DbName("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @DbName("pet_id") Lazy<Pet, Integer> pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertLazyNullablePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithLazyNullablePet.class);
            VisitWithLazyNullablePet visit = new VisitWithLazyNullablePet(1, LocalDate.now(), "test", Lazy.ofNull(), Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testUpdateLazyNullablePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithLazyNullablePet.class);
            var visit = repository.select(1);
            repository.update(visit.toBuilder().pet(Lazy.ofNull()).build());
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testInsertLazyNullablePetWithNonnull() {
        var repository = ORM(dataSource).entity(VisitWithLazyNullablePet.class);
        VisitWithLazyNullablePet visit = new VisitWithLazyNullablePet(1, LocalDate.now(), "test", Lazy.of(Pet.builder().id(1).build()), Instant.now());
        var id = repository.insertAndFetchId(visit);
        var visitFromDb = repository.select(id);
        assertEquals(id, visitFromDb.id());
        assertFalse(visitFromDb.pet().isNull());
        assertEquals(1, visitFromDb.pet().fetch().id());
    }

    @DbName("pet")
    public record PetWithLazyNullableOwner(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @DbName("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @DbName("type_id") @Persist(updatable = false) PetType petType,
            @FK @DbName("owner_id") Lazy<Owner, Integer> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectLazyNullOwner() {
        try (var stream = ORM(dataSource).entity(PetWithLazyNullableOwner.class).selectAll()) {
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
    @DbName("visit")
    public record VisitWithTwoPets(
            @PK Integer id,
            @Nonnull @DbName("visit_date") LocalDate visitDate,
            @Nullable String description,
            @FK @DbName("pet_id") @Qualifier("mom") PetLazyOwner pet1,
            @FK @DbName("pet_id") @Qualifier("dad") PetLazyOwner pet2
    ) implements Entity<Integer> {
    }

    @Builder(toBuilder = true)
    @DbName("pet")
    public record PetLazyOwner(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @DbName("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @DbName("type_id") @Persist(updatable = false) PetType petType,
            @FK @DbName("owner_id") Lazy<Owner, Integer> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectWithTwoPetsWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).selectFrom(Owner.class).getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class).select().where(owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithExpression() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.expression(RAW."\{PetLazyOwner.class}.owner_id = \{owner.id()}")).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithWhere() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(RAW."\{PetLazyOwner.class}.owner_id = \{owner.id()}").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithPath() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var list = ORM(dataSource).entity(VisitWithTwoPets.class).select().where("pet1.owner", EQUALS, owner).getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testSelectWithTwoPetsWithPathTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM(dataSource).entity(VisitWithTwoPets.class)
                .select()
                .where(it -> it.expression(RAW."\{alias(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsWithMultipleParameters() throws Exception {
        var orm = ORM(dataSource);
        var owner = orm.entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = orm.entity(VisitWithTwoPets.class).select().where(it -> it.filter("pet1.owner", EQUALS, owner).or(it.filter("pet2.owner", EQUALS, owner))).getResultList();
            assertEquals(2, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }


    @Test
    public void testSelectWithTwoPetsWithMultipleParametersTemplate() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.expression(RAW."\{alias(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()} OR \{alias(PetLazyOwner.class, "pet2")}.owner_id = \{owner.id()}")).getResultList();
            assertEquals(2, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Builder(toBuilder = true)
    @DbName("visit")
    public record VisitWithTwoPetsOneLazy(
            @PK Integer id,
            @Nonnull @DbName("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @DbName("pet_id") PetLazyOwner pet1,
            @Nullable @FK @DbName("pet_id") Lazy<PetLazyOwner, Integer> pet2
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithoutPath() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class).select().where(owner).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithoutPathTemplate() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class)
                    .select()
                    .where(it -> it.expression(RAW."\{PetLazyOwner.class}.owner_id = \{owner.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithPath() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class).select().where("pet1.owner", EQUALS, owner).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyWithPathTemplate() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class)
                    .select()
                    .where(it -> it.expression(RAW."\{alias(PetLazyOwner.class, "pet1")}.owner_id = \{owner.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var pet = ORM(dataSource).entity(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class).select().where(pet).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithoutPathTemplate() {
        // Note that this test is not comparable to the previous test because of the re-use of pet_id.
        var pet = ORM(dataSource).entity(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class)
                .select()
                .where(it -> it.expression(RAW."\{PetLazyOwner.class}.id = \{pet.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithPath() throws Exception {
        var pet = ORM(dataSource).entity(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class).select().where("pet1", EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyPetWithPathTemplate() throws Exception {
        var ORM = ORM(dataSource);
        var pet = ORM.entity(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM.entity(VisitWithTwoPetsOneLazy.class)
                    .select()
                    .where(it -> it.expression(RAW."\{alias(PetLazyOwner.class, "pet1")}.id = \{pet.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyOtherPetWithPath() throws Exception {
        var pet = ORM(dataSource).entity(PetLazyOwner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        try (var _ = intercept(sql::setPlain)) {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class).select().where("pet2", EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        }
    }

    @Test
    public void testSelectWithTwoPetsOneLazyOtherPetWithPathTemplate() {
        // Note that this test is not comparable to the previous test because no join should exist for pet2 as it's
        // lazy, and therefore pet.id will be used.
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(VisitWithTwoPetsOneLazy.class)
                    .select()
                    .where(it -> it.expression(RAW."\{alias(PetLazyOwner.class, "pet2")}.id = \{1}")).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @DbName("visit")
    public record VisitWithTwoLazyPets(
            @PK Integer id,
            @Nonnull @DbName("visit_date") LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK @DbName("pet_id") Lazy<PetLazyOwner, Integer> pet1,
            @Nullable @FK @DbName("pet_id") Lazy<PetLazyOwner, Integer> pet2
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectWithTwoLazyPetsWithoutPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoLazyPets.class).select().where(owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsWithoutPathTemplate() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoLazyPets.class)
                    .select()
                    .where(it -> it.expression(RAW."\{PetLazyOwner.class}.id = \{owner.id()}")) // Cannot find PetLazyOwner alias as lazy fields are not joined.
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoLazyPetsWithPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoLazyPets.class).select().where("pet1.owner", EQUALS, owner).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePath() {
        var list = ORM(dataSource).entity(Owner.class).select().where("address.city", EQUALS, "Madison").getResultList();
        assertEquals(4, list.size());
    }

    @Test
    public void testSelectWithInlinePathEqualsMultiArgument() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Owner.class).select().where("address.city", EQUALS, "Madison", "Monona").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePathInMultiArgument() {
        var list = ORM(dataSource).entity(Owner.class).select().where("address.city", IN, "Madison", "Monona").getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testSelectWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(Pet.class)
                .select()
                .where(owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(PetWithLazyNullableOwner.class)
                .select()
                .where(owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectLazyWhereWithJoin() {
        var e = assertThrows(PersistenceException.class, () -> {
            Owner owner = Owner.builder().id(1).build();
            ORM(dataSource).entity(PetWithLazyNullableOwner.class)
                    .select()
                    .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                    .where(owner)
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectLazyWhereWithJoinAndPath() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(PetWithLazyNullableOwner.class)
                .select()
                .innerJoin(Owner.class).on(PetWithLazyNullableOwner.class)
                .where("owner", EQUALS, owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectNullableOwner() {
        // Lazy elements are not joined by default. Test whether join works.
        var owners = ORM(dataSource)
                .selectFrom(Pet.class, Owner.class, RAW."\{Owner.class}")
                .getResultList();
        assertEquals(12, owners.size());
    }

    @Test
    public void testCustomRepo1() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById1(1));
    }

    @Test
    public void testCustomRepo2() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById2(1));
    }

    @Test
    public void testCustomRepo3() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        var pet = repo.select(1);
        assertEquals(pet, repo.findById3(1));
    }

    @Test
    public void testCustomRepo4() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        assertEquals(1, repo.findByOwnerFirstName("Betty").size());
    }

    @Test
    public void testCustomRepo5() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        assertEquals(4, repo.findByOwnerCity("Madison").size());
    }

    @Test
    public void testPetVisitCount() {
        var repo = ORM(dataSource).proxy(PetRepository.class);
        assertEquals(8, repo.petVisitCount().size());
    }

    @Test
    public void delete() {
        var R = ORM(dataSource).entity(Visit.class);
        R.delete(Visit.builder().id(1).build());
        assertEquals(13, R.select().getResultCount());
    }

    @Test
    public void deleteAll() {
        var R = ORM(dataSource).entity(Visit.class);
        R.deleteAll();
        assertEquals(0, R.select().getResultCount());
    }

    @Test
    public void deleteBatch() {
        var R = ORM(dataSource).entity(Visit.class);
        try (var stream = R.selectAll()) {
            R.delete(stream);
        }
        assertEquals(0, R.count());
    }

    @Test
    public void testBuilder() {
        var list = ORM(dataSource).entity(Pet.class)
                .select()
                .where(RAW."\{Pet.builder().id(1).build()}")
                .getResultList();
        assertEquals(1, list.size());
    }

    @Test
    public void testBuilderWithJoin() {
        var list = ORM(dataSource)
                .entity(Pet.class)
                .select()
                .innerJoin(Visit.class).on(RAW."\{alias(Pet.class)}.id = \{Visit.class}.pet_id")
                .where(RAW."\{alias(Visit.class)}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
        assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var list = ORM(dataSource)
                .entity(Pet.class)
                .select()
                .innerJoin(Visit.class).on(Pet.class)
                .where(Visit.builder().id(1).build())
                .getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoAndCustomJoin() {
        var list = ORM(dataSource)
                .entity(Pet.class)
                .select()
                .innerJoin(Visit.class).on(Pet.class)
                .innerJoin(RAW."SELECT * FROM \{Pet.class}", "x").on(RAW."\{Pet.class}.id = x.id")    // Join just for the sake of testing multiple joins.
                .where(Visit.builder().id(1).build())
                .getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
                    .entity(Pet.class)
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
        var list = ORM(dataSource)
                .selectFrom(Pet.class, Result.class, RAW."\{alias(Pet.class)}.id, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .append(RAW."GROUP BY \{alias(Pet.class)}.id")
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
        var list = ORM(dataSource)
                .selectFrom(Pet.class, Result.class, RAW."\{alias(Pet.class)}.id, COUNT(*)")
                .join(INNER, RAW."SELECT * FROM \{table(Visit.class, "a")} WHERE \{alias(Visit.class)}.id > \{-1}", "x").on(RAW."\{Pet.class}.id = x.pet_id")
                .append(RAW."GROUP BY \{alias(Pet.class)}.id")
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithSubqueryJoin() {
        record Result(int petId, int visitCount) {}
        var orm = ORM(dataSource);
        var list = orm
                .selectFrom(Pet.class, Result.class, RAW."\{alias(Pet.class)}.id, COUNT(*)")
                .join(INNER, orm.subquery(Visit.class), "x").on(RAW."\{Pet.class}.id = x.pet_id")
                .append(RAW."GROUP BY \{alias(Pet.class)}.id")
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testWithArg() {
        var list = ORM(dataSource).entity(Pet.class).select().where(template(it -> STR."\{it.invoke(Pet.class)}.id = 7")).getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
        ORM(dataSource).entity(Pet.class).count(list.stream().map(Pet::id));
    }

    @Test
    public void testWithTwoArgs() {
        var ORM = ORM(dataSource);
        var list = ORM.entity(Pet.class).select().where(template(it -> STR."\{it.invoke(Pet.class)}.id = 7 OR \{it.invoke(Pet.class)}.id = 8")).getResultList();
        assertEquals(2, list.size());
        assertEquals(7, list.getFirst().id());
        assertEquals(8, list.getLast().id());
    }

    @Test
    public void updateOwnerIntegerVersion() {
        var repo = ORM(dataSource).entity(Owner.class);
        Owner owner = repo.select(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        Owner updatedOwner = repo.updateAndFetch(modifiedOwner);
        repo.update(updatedOwner);
    }

    @Test
    public void testUpdateOwnerWrongIntegerVersion() {
        var repo = ORM(dataSource).entity(Owner.class);
        Owner owner = repo.select(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        repo.update(modifiedOwner);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedOwner));
    }

    @Test
    public void testUpdateVisitTimestampVersion() {
        var repo = ORM(dataSource).entity(Visit.class);
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        Visit updatedVisit = repo.updateAndFetch(modifiedVisit);
        repo.update(updatedVisit);
    }

    @Test
    public void testUpdateVisitWrongTimestampVersion() {
        var repo = ORM(dataSource).entity(Visit.class);
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        repo.update(modifiedVisit);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedVisit));
    }

    @Test
    public void testWhereExists() {
        var list = ORM(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(RAW."\{alias(Owner.class, "")}.id = \{alias(Owner.class, "pet.owner")}.id")))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsAmbiguous() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Owner.class)
                    .select()
                    .where(it -> it.exists(it.subquery(Visit.class).where(RAW."\{Owner.class}.id = \{Owner.class}.id")))
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testWhereExistsPredicateAmbiguous() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Owner.class)
                    .select()
                    .where(it ->
                            it.expression(RAW."EXISTS (\{
                                    it.subquery(Visit.class).where(RAW."\{Owner.class}.id = \{Owner.class}.id")
                            })"))
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testWhereExistsPredicate() {
        var list = ORM(dataSource).entity(Owner.class)
                .select()
                .where(it ->
                        it.expression(RAW."EXISTS (\{
                                it.subquery(Visit.class).where(RAW."\{alias(Owner.class, OUTER)}.id = \{alias(Owner.class, ResolveScope.INNER)}.id")
                        })"))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsAppendSubquery() {
        var orm = ORM(dataSource);
        var list = ORM(dataSource).entity(Owner.class)
                .select()
                .append(RAW."WHERE EXISTS (\{
                        orm.subquery(Visit.class).where(RAW."\{alias(Owner.class, "")}.id = \{alias(Owner.class, "pet.owner")}.id")
                })")
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereAppendQuery() {
        // We cannot append a query, we need to use a query builder instead.
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Owner.class)
                    .select()
                    .append(RAW."WHERE (\{ORM(dataSource).query(RAW."SELECT 1")})")
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testWherePredicateSubqueryParameters() {
        String expectedSql = """
            SELECT _o.id, _o.first_name, _o.last_name, _o.address, _o.city, _o.telephone, _o.version
            FROM owner _o
            WHERE id = ? AND (EXISTS (
              SELECT _o1.id, _o1.first_name, _o1.last_name, _o1.address, _o1.city, _o1.telephone, _o1.version
              FROM owner _o1
              WHERE id = ?
            )) AND (3 = ?)""";
        try (var _ = SqlInterceptor.intercept(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertTrue(sql.parameters().get(0) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 1 && Integer.valueOf(1).equals(dbValue));
            assertTrue(sql.parameters().get(1) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 2 && Integer.valueOf(2).equals(dbValue));
            assertTrue(sql.parameters().get(2) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 3 && Integer.valueOf(3).equals(dbValue));
        })) {
            var orm = ORM(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .where(it -> it.expression(RAW."id = \{1}")
                            .and(it.expression(RAW."EXISTS (\{it.subquery(Owner.class).where(RAW."id = \{2}")})"))
                            .and(it.expression(RAW."3 = \{3}"))
                    )
                    .getResultList();
        }
    }

    @Test
    public void testWhereAppendQueryBuilderParameters() {
        String expectedSql = """
            SELECT _o.id, _o.first_name, _o.last_name, _o.address, _o.city, _o.telephone, _o.version
            FROM owner _o
            WHERE id = ?
            AND EXISTS (
              SELECT _o1.id, _o1.first_name, _o1.last_name, _o1.address, _o1.city, _o1.telephone, _o1.version
              FROM owner _o1
              WHERE id = ?
            )
            AND 3 = ?""";
        try (var _ = SqlInterceptor.intercept(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertTrue(sql.parameters().get(0) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 1 && Integer.valueOf(1).equals(dbValue));
            assertTrue(sql.parameters().get(1) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 2 && Integer.valueOf(2).equals(dbValue));
            assertTrue(sql.parameters().get(2) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 3 && Integer.valueOf(3).equals(dbValue));
        })) {
            var orm = ORM(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .append(RAW."WHERE id = \{1}")
                    .append(RAW."AND EXISTS (\{orm.subquery(Owner.class).where(RAW."id = \{2}")})")
                    .append(RAW."AND 3 = \{3}")
                    .getResultList();
        }
    }
}