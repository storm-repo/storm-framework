package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Templates.SelectMode;
import st.orm.model.City;
import st.orm.model.Owner;
import st.orm.model.Owner_;
import st.orm.model.Pet;
import st.orm.model.PetOwnerRecursion;
import st.orm.model.PetOwnerRef;
import st.orm.model.PetOwnerRef_;
import st.orm.model.PetTypeEnum;
import st.orm.model.PetWithNullableOwnerRef;
import st.orm.model.PetWithNullableOwnerRef_;
import st.orm.model.Pet_;
import st.orm.model.Specialty;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.VetSpecialtyPK;
import st.orm.model.Visit;
import st.orm.model.VisitWithCompoundFK;
import st.orm.model.VisitWithCompoundFK_;
import st.orm.model.VisitWithTwoPetRefs;
import st.orm.model.VisitWithTwoPets;
import st.orm.model.VisitWithTwoPetsOneRef;
import st.orm.model.VisitWithTwoPetsOneRef_;
import st.orm.model.VisitWithTwoPets_;
import st.orm.model.Visit_;
import st.orm.repository.Entity;
import st.orm.repository.PetRepository;
import st.orm.template.Metamodel;
import st.orm.template.Sql;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplateException;
import st.orm.template.impl.DefaultJoinType;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import static st.orm.EnumType.ORDINAL;
import static st.orm.Templates.ORM;
import static st.orm.Templates.alias;
import static st.orm.Templates.column;
import static st.orm.Templates.select;
import static st.orm.Templates.where;
import static st.orm.template.Operator.BETWEEN;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.GREATER_THAN;
import static st.orm.template.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.template.Operator.IN;
import static st.orm.template.Operator.IS_NULL;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.ResolveScope.OUTER;
import static st.orm.template.SqlInterceptor.observe;
import static st.orm.template.TemplateFunction.template;

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
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByFkNested() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where(Visit_.pet.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        assertEquals(1, ORM(dataSource).entity(Visit.class).select().where(Visit_.visitDate, EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        assertEquals(13, ORM(dataSource).entity(Visit.class).select().where(Visit_.visitDate, GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        assertEquals(14, ORM(dataSource).entity(Visit.class).select().where(Visit_.visitDate, GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnBetween() {
        assertEquals(10, ORM(dataSource).entity(Visit.class).select().where(Visit_.visitDate, BETWEEN, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 9)).getResultCount());
    }

    @Test
    public void testSelectByColumnIsNull() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByColumnRefIsNull() {
        assertEquals(1, ORM(dataSource).entity(PetWithNullableOwnerRef.class).select().where(PetWithNullableOwnerRef_.owner, IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByColumnEqualsNull() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, EQUALS, (Owner) null).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectByColumnRefEqualsNull() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(PetWithNullableOwnerRef.class).select().where(PetWithNullableOwnerRef_.owner, EQUALS, (Owner) null).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectByColumnEqualsId() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Pet_.owner.id, EQUALS, 1).getResultCount());
    }

    @Test
    public void testSelectByColumnRecord() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, EQUALS, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumnRef() {
        assertEquals(1, ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, Ref.of(Owner.builder().id(1).build())).getResultCount());
    }

    @Test
    public void testSelectByColumnNullRef() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Pet.class).select().where(Pet_.owner, Ref.ofNull()).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());

    }

    @Test
    public void testSelectByNestedColumn() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where(Visit_.pet.name, EQUALS, "Leo").getResultCount());
    }

    @Test
    public void testSelectByDeeperNestedColumn() {
        assertEquals(2, ORM(dataSource).entity(Visit.class).select().where(Visit_.pet.owner.firstName, EQUALS, "Betty").getResultCount());
    }

    @Test
    public void testSelectByUnknownColumn() throws SqlTemplateException{
        var e = assertThrows(PersistenceException.class, () -> {
            var model = Visit_.pet.name;
            // We don't want to use Metamodel.of as it would already throw an exception.
            Metamodel<Visit, String> invalidModel = new Metamodel<>() {
                @Override
                public boolean isColumn() {
                    return true;
                }

                @Override
                public Class<Visit> root() {
                    return model.root();
                }

                @Override
                public Metamodel<Visit, ? extends Record> table() {
                    return model.table();
                }

                @Override
                public String path() {
                    return model.path();
                }

                @Override
                public Class<String> componentType() {
                    return model.componentType();
                }

                @Override
                public String component() {
                    return "names"; // Invalid name.
                }
            };
            ORM(dataSource).entity(Visit.class).select().where(RAW."\{where(invalidModel, EQUALS, "Leo")}").getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectCountCustomClass() {
        record Count(Owner owner, int value) {}
        var query = ORM(dataSource).query(RAW."SELECT \{Owner.class}, COUNT(*) FROM \{Pet.class} GROUP BY \{Owner_.id}");
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
        var list2 = repository.findAllById(list.stream().map(Vet::id).toList());
        assertEquals(list, list2);
    }

    @Test
    public void testSelectFetchRef() {
        var visit = ORM(dataSource).entity(VisitWithNullablePetRef.class).getById(1);
        //noinspection DataFlowIssue
        var pet = visit.pet().fetch();
        assertFalse(visit.pet().isNull());
        //noinspection DataFlowIssue
        assertEquals("Jean", pet.owner().firstName());
        assertSame(pet, visit.pet().fetch());
    }

    @Test
    public void testSelectRef() {
        var visits = ORM(dataSource).entity(Visit.class).selectRef().getResultList();
        assertEquals(14, visits.size());
    }

    record VisitResult(Ref<Visit> visit) {}

    @Test
    public void testSelectRefTemplate() {
        var visits = ORM(dataSource).selectFrom(Visit.class, VisitResult.class, RAW."\{select(Visit.class, SelectMode.PK)}").getResultList();
        assertEquals(14, visits.size());
    }

    @Test
    public void testSelectRefCustomType() {
        var pets = ORM(dataSource).entity(Visit.class).selectRef(Pet.class).getResultList();
        assertEquals(14, pets.size());
    }

    @Test
    public void testSelectWhereRef() {
        var owners = ORM(dataSource).entity(Owner.class).select().where(Ref.of(Owner.builder().id(1).build())).getResultList();
        assertEquals(1, owners.size());
        assertEquals(1, owners.getFirst().id());
    }

    @Test
    public void testSelectRefCompoundPk() {
        var vetSpecialties = ORM(dataSource).entity(VetSpecialty.class).selectRef().getResultList();
        assertEquals(5, vetSpecialties.size());
    }

    @Test
    public void testSelectWhereRefCompoundPk() {
        var vetSpecialties = ORM(dataSource).entity(VetSpecialty.class).select().where(Ref.of(VetSpecialty.builder().id(new VetSpecialtyPK(2, 1)).build())).getResultList();
        assertEquals(1, vetSpecialties.size());
        assertEquals(2, vetSpecialties.getFirst().vet().id());
        assertEquals(1, vetSpecialties.getFirst().specialty().id());
    }

    @Test
    public void testSelectWithInvalidPath() {
        var e = assertThrows(PersistenceException.class, () ->
                ORM(dataSource).entity(Pet.class).select()
                        .where(RAW."\{where(Metamodel.of(Pet.class, "owner.city"), EQUALS, "Sunnyvale")}")
                        .getResultList());
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testWhereWithOperatorAndRecord() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).getById(1);
            ORM(dataSource).entity(Pet.class).select()
                    .where(RAW."\{Pet_.owner} = \{owner}")
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInvalidPathNoArg() {
        var e = assertThrows(PersistenceException.class, () ->
                ORM(dataSource).entity(Pet.class).select()
                        .where(RAW."\{where(Metamodel.of(Pet.class, "owner.city"), IS_NULL)}")
                        .getResultList());
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testInsertPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(Visit.class);
            //noinspection DataFlowIssue
            Visit visit = new Visit(0, LocalDate.now(), "test", null, Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(Visit.class);
            var visit = repository.getById(1);
            //noinspection DataFlowIssue
            repository.update(new Visit(visit.id(), visit.visitDate(), visit.description(), null, Instant.now()));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitWithNonnullPetRef(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Ref<Pet> pet
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertPetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithNonnullPetRef.class);
            VisitWithNonnullPetRef visit = new VisitWithNonnullPetRef(0, LocalDate.now(), "test", Ref.ofNull());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithNonnullPetRef.class);
            var visit = repository.getById(1);
            //noinspection DataFlowIssue
            repository.update(new VisitWithNonnullPetRef(visit.id(), visit.visitDate(), visit.description(), null));
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitWithNullablePetRef(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nullable @FK Ref<Pet> pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {
    }

    @Test
    void testInsertNullablePetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithNullablePetRef.class);
            VisitWithNullablePetRef visit = new VisitWithNullablePetRef(0, LocalDate.now(), "test", Ref.ofNull(), Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testUpdateNullablePetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORM(dataSource).entity(VisitWithNullablePetRef.class);
            var visit = repository.getById(1);
            repository.update(visit.toBuilder().pet(Ref.ofNull()).build());
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testInsertNullablePetRefWithNonnull() {
        var visitRepository = ORM(dataSource).entity(VisitWithNullablePetRef.class);
        VisitWithNullablePetRef visit = new VisitWithNullablePetRef(0, LocalDate.now(), "test", Ref.of(Pet.builder().id(1).build()), Instant.now());
        var id = visitRepository.insertAndFetchId(visit);
        var visitFromDb = visitRepository.getById(id);
        assertEquals(id, visitFromDb.id());
        //noinspection DataFlowIssue
        assertFalse(visitFromDb.pet().isNull());
        assertEquals(1, visitFromDb.pet().fetch().id());
    }

    @Test
    public void testSelectNullOwnerRef() {
        try (var stream = ORM(dataSource).entity(PetWithNullableOwnerRef.class).selectAll()) {
            var pet = stream.filter(p -> p.name().equals("Sly")).findFirst().orElseThrow();
            assertTrue(pet.owner().isNull());
            assertNull(pet.owner().fetch());
        }
    }

    @Test
    public void testSelectRefInnerJoin() {
        // Ref elements are not joined by default. Test whether join works.
        var owners = ORM(dataSource)
                .selectFrom(PetWithNullableOwnerRef.class, Owner.class, RAW."DISTINCT \{Owner.class}")
                .innerJoin(Owner.class).on(PetWithNullableOwnerRef.class)
                .innerJoin(City.class).on(Owner.class)
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

    @Test
    public void testSelectWithWrapperNullOwner() {
        record Wrapper(Pet pet) {}
        var wrapper = ORM(dataSource)
                .selectFrom(Pet.class, Wrapper.class)
                .where(Pet_.id, EQUALS, 13)
                .getSingleResult();
        assertEquals(13, wrapper.pet().id());
        assertNull(wrapper.pet().owner());
    }

    @Test
    public void testSelectWithNonnullWrapperNullOwner() {
        record OwnerWrapper(@Nullable @FK Owner owner) {}
        record Pet(
                @PK Integer id,
                @Nonnull OwnerWrapper owner
        ) implements Entity<Integer> {}
        record Wrapper(Pet pet) {}
        var wrapper = ORM(dataSource)
                .selectFrom(Pet.class, Wrapper.class)
                .where(RAW."\{Pet.class}.id = \{13}")
                .getSingleResult();
        assertEquals(13, wrapper.pet().id());
        assertNull(wrapper.pet().owner().owner());
    }

    @Test
    public void testSelectWithTwoPetsWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).selectFrom(Owner.class).getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class).select().where(it -> it.whereAny(owner)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithExpression() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(RAW."\{PetOwnerRef.class}.owner_id = \{owner.id()}")).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithExpressionMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(RAW."\{PetOwnerRef_.owner} = \{owner.id()}")).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithWhere() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(RAW."\{PetOwnerRef.class}.owner_id = \{owner.id()}").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithWhereMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(RAW."\{PetOwnerRef_.owner} = \{owner.id()}").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithPathRaw() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var list = ORM(dataSource).entity(VisitWithTwoPets.class).select().append(RAW."WHERE \{where(VisitWithTwoPets_.pet1.owner, EQUALS, owner)}").getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testSelectWithTwoPetsWithPathTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM(dataSource).entity(VisitWithTwoPets.class)
                .select()
                .where(it -> it.where(RAW."\{VisitWithTwoPets_.pet1.owner} = \{owner.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsWithMultipleParameters() {
        var orm = ORM(dataSource);
        var owner = orm.entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = orm.entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(VisitWithTwoPets_.pet1.owner, EQUALS, owner)
                            .or(it.where(VisitWithTwoPets_.pet2.owner, EQUALS, owner))).getResultList();
            assertEquals(2, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsWithMultipleParametersTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(RAW."\{VisitWithTwoPets_.pet1.owner} = \{owner.id()} OR \{VisitWithTwoPets_.pet2.owner} = \{owner.id()}")).getResultList();
            assertEquals(2, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testPetOwnerRecursion() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(PetOwnerRecursion.class).select().getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithoutPath() throws Exception {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(it -> it.whereAny(owner)).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithoutPathTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(RAW."\{PetOwnerRef.class}.owner_id = \{owner.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithRootPathTemplateMetamodel() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var list = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(RAW."\{PetOwnerRef_.owner} = \{owner.id()}")).getResultList();
            assertEquals(2, list.size());
            //noinspection DataFlowIssue
            assertEquals(owner.id(), list.getFirst().pet1().owner().id());
            //noinspection DataFlowIssue
            assertEquals(owner.id(), list.getLast().pet1().owner().id());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithInvalidPathTemplateMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(it -> it.where(RAW."\{Pet_.owner} = \{owner.id()}")).getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithInvalidPathMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(it -> it.whereAny(PetOwnerRef_.owner, owner)).getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithRootPathMetamodelTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var list = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(RAW."\{PetOwnerRef_.owner} = \{owner.id()}").getResultList();
            assertEquals(2, list.size());
            //noinspection DataFlowIssue
            assertEquals(owner.id(), list.getFirst().pet1().owner().id());
            //noinspection DataFlowIssue
            assertEquals(owner.id(), list.getLast().pet1().owner().id());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithInvalidPathMetamodelTemplate() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(RAW."\{Pet_.owner} = \{owner.id()}").getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithPath() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet1.owner, EQUALS, owner).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithPathTemplate() {
        var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(RAW."\{VisitWithTwoPetsOneRef_.pet1.owner} = \{owner.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var pet = ORM(dataSource).entity(PetOwnerRef.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(it -> it.whereAny(pet)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithoutPathTemplate() {
        // Note that this test is not comparable to the previous test because of the re-use of pet_id.
        var pet = ORM(dataSource).entity(PetOwnerRef.class).select().append(RAW."LIMIT 1").getSingleResult();
        var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                .select()
                .where(it -> it.where(RAW."\{PetOwnerRef.class}.id = \{pet.id()}")).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithPath() {
        var pet = ORM(dataSource).entity(PetOwnerRef.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet1, EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithPathTemplateMetamodel() throws Exception {
        var ORM = ORM(dataSource);
        var pet = ORM.entity(PetOwnerRef.class).select().append(RAW."LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM.entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(RAW."\{VisitWithTwoPetsOneRef_.pet1} = \{pet.id()}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefOtherPetWithPath() {
        var pet = ORM(dataSource).entity(PetOwnerRef.class).getById(1);
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet2, EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefOtherPetWithPathTemplateMetamodel() {
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(RAW."\{VisitWithTwoPetsOneRef_.pet2} = \{1}")).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetRefsWithoutPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoPetRefs.class).select().where(it -> it.whereAny(owner)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetRefsWithoutPathTemplate() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORM(dataSource).entity(Owner.class).select().append(RAW."LIMIT 1").getSingleResult();
            ORM(dataSource).entity(VisitWithTwoPetRefs.class)
                    .select()
                    .where(it -> it.where(RAW."\{PetOwnerRef.class}.id = \{owner.id()}")) // Cannot find PetOwnerRef alias as ref fields are not joined.
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testInternerRecord() {
        var pets = ORM(dataSource).entity(Pet.class).select().getResultList();
        var owners = Collections.newSetFromMap(new IdentityHashMap<>());
        owners.addAll(pets.stream().map(Pet::owner).toList());
        assertEquals(11, owners.size());
    }

    @Test
    public void testInternerRef() {
        var pets = ORM(dataSource).entity(PetOwnerRef.class).select().getResultList();
        var owners = Collections.newSetFromMap(new IdentityHashMap<>());
        owners.addAll(pets.stream().map(PetOwnerRef::owner).toList());
        assertEquals(11, owners.size());
    }

    @Test
    public void testSelectWithInlinePath() {
        var list = ORM(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, EQUALS, "Madison").getResultList();
        assertEquals(4, list.size());
    }

    @Test
    public void testSelectWithInlinePathEqualsMultiArgument() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, EQUALS, "Madison", "Monona").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePathInMultiArgument() {
        var list = ORM(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, IN, "Madison", "Monona").getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testSelectWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(Pet.class)
                .select()
                .where(it -> it.whereAny(owner))
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectRefWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(PetWithNullableOwnerRef.class)
                .select()
                .where(it -> it.whereAny(owner))
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectRefWhereWithJoin() {
        var e = assertThrows(PersistenceException.class, () -> {
            Owner owner = Owner.builder().id(1).build();
            ORM(dataSource).entity(PetWithNullableOwnerRef.class)
                    .select()
                    .innerJoin(Owner.class).on(PetWithNullableOwnerRef.class)
                    .where(it -> it.whereAny(owner))
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectRefWhereWithJoinAndPath() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORM(dataSource).entity(PetWithNullableOwnerRef.class)
                .select()
                .innerJoin(Owner.class).on(PetWithNullableOwnerRef.class)
                .where(PetWithNullableOwnerRef_.owner, EQUALS, owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectNullableOwner() {
        // Ref elements are not joined by default. Test whether join works.
        var owners = ORM(dataSource)
                .selectFrom(Pet.class, Owner.class, RAW."\{Owner.class}")
                .getResultList();
        assertEquals(12, owners.size());
    }

    @Test
    public void testCustomRepo1() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById1(1));
    }

    @Test
    public void testCustomRepo2() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById2(1));
    }

    @Test
    public void testCustomRepo3() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById3(1));
    }

    @Test
    public void testCustomRepo4() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        assertEquals(1, repo.findByOwnerFirstName("Betty").size());
    }

    @Test
    public void testCustomRepo5() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        assertEquals(4, repo.findByOwnerCity("Madison").size());
    }

    @Test
    public void testPetVisitCount() {
        var repo = ORM(dataSource).repository(PetRepository.class);
        assertEquals(8, repo.petVisitCount().size());
    }

    @Test
    public void delete() {
        var repo = ORM(dataSource).entity(Visit.class);
        repo.delete(Visit.builder().id(1).build());
        assertEquals(13, repo.select().getResultCount());
    }

    @Test
    public void deleteByPet() {
        var repo = ORM(dataSource).entity(Visit.class);
        repo.delete().where(it -> it.whereAny(Pet.builder().id(1).build())).executeUpdate();
        assertEquals(12, repo.select().getResultCount());
    }

    @Test
    public void deleteByOwner() {
        var repo = ORM(dataSource).entity(Visit.class);
        repo.delete().where(it -> it.whereAny(Owner.builder().id(1).build())).executeUpdate();
        assertEquals(12, repo.select().getResultCount());
    }

    @Test
    public void deleteAll() {
        var repo = ORM(dataSource).entity(Visit.class);
        repo.deleteAll();
        assertEquals(0, repo.select().getResultCount());
    }

    @Test
    public void deleteBatch() {
        var repo = ORM(dataSource).entity(Visit.class);
        try (var stream = repo.selectAll()) {
            repo.delete(stream);
        }
        assertEquals(0, repo.count());
    }

    @Test
    public void deleteRefBatch() {
        var repo = ORM(dataSource).entity(Visit.class);
        try (var stream = repo.selectAll().map(Ref::of)) {
            repo.deleteByRef(stream);
        }
        assertEquals(0, repo.count());
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
                .where(it -> it.whereAny(Visit.builder().id(1).build()))
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
                .where(it -> it.whereAny(Visit.builder().id(1).build()))
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
                    .where(it -> it.whereAny(Vet.builder().id(1).build()))
                    .getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithSelectTemplate() {
        record Result(int petId, int visitCount) {}
        var list = ORM(dataSource)
                .selectFrom(Pet.class, Result.class, RAW."\{Pet_.id}, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
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
                .selectFrom(Pet.class, Result.class, RAW."\{Pet_.id}, COUNT(*)")
                .join(DefaultJoinType.INNER, RAW."SELECT * FROM \{Visit.class} WHERE \{Visit.class}.id > \{-1}", "x").on(RAW."\{Pet.class}.id = x.pet_id")
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithSubqueryJoin() {
        record Result(int petId, int visitCount) {}
        var orm = ORM(dataSource);
        var list = orm
                .selectFrom(Pet.class, Result.class, RAW."\{Pet_.id}, COUNT(*)")
                .join(DefaultJoinType.INNER, orm.subquery(Visit.class), "x").on(RAW."\{Pet_.id} = x.pet_id")
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testWithArg() {
        var list = ORM(dataSource).entity(Pet.class).select().where(template(it -> STR."\{it.invoke(Pet.class)}.id = 7")).getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
        ORM(dataSource).entity(Pet.class).countById(list.stream().map(Pet::id));
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
    public void selectOwnerForUpdate() {
        // Note that H2 only supports FOR UPDATE.
        String expectedSql = """
            SELECT o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
            FROM owner o
            LEFT JOIN city c ON o.city_id = c.id
            WHERE o.id = ?
            FOR UPDATE""";
        var repo = ORM(dataSource).entity(Owner.class);
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            repo.select().forUpdate().where(1).getSingleResult();
        });
    }

    @Test
    public void updateOwnerIntegerVersion() {
        var repo = ORM(dataSource).entity(Owner.class);
        Owner owner = repo.getById(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        Owner updatedOwner = repo.updateAndFetch(modifiedOwner);
        repo.update(updatedOwner);
    }

    @Test
    public void testUpdateOwnerWrongIntegerVersion() {
        var repo = ORM(dataSource).entity(Owner.class);
        Owner owner = repo.getById(1);
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
                .where(it -> it.exists(it.subquery(Visit.class).where(RAW."\{alias(Owner.class, OUTER)}.id = \{alias(Owner.class, INNER)}.id")))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsMetamodel() {
        var list = ORM(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(RAW."\{column(Owner_.id, OUTER)} = \{column(Owner_.id, INNER)}")))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsCascade() {
        // The Owner.id = Owner.id is not ambiguous because they are in different scopes.
        ORM(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(RAW."\{Owner.class}.id = \{Owner.class}.id")))
                .getResultList();
    }

    @Test
    public void testWhereExistsPredicateCascade() {
        // The Owner.id = Owner.id is not ambiguous because they are in different scopes.
        ORM(dataSource).entity(Owner.class)
                .select()
                .where(it ->
                        it.where(RAW."EXISTS (\{
                                it.subquery(Visit.class).where(RAW."\{Owner.class}.id = \{Owner.class}.id")
                        })"))
                .getResultList();
    }

    @Test
    public void testWhereExistsPredicate() {
        var list = ORM(dataSource).entity(Owner.class)
                .select()
                .where(it ->
                        it.where(RAW."EXISTS (\{
                                it.subquery(Visit.class).where(RAW."\{alias(Owner.class, OUTER)}.id = \{alias(Owner.class, INNER)}.id")
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
                        orm.subquery(Visit.class).where(RAW."\{alias(Owner.class, OUTER)}.id = \{alias(Owner.class, INNER)}.id")
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
            SELECT o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
            FROM owner o
            LEFT JOIN city c ON o.city_id = c.id
            WHERE o.id = ? AND EXISTS (
              SELECT o1.id, o1.first_name, o1.last_name, o1.address, o1.city_id, o1.telephone, o1.version
              FROM owner o1
              WHERE o1.id = ?
            ) AND 3 = ?""";
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertTrue(sql.parameters().get(0) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 1 && Integer.valueOf(1).equals(dbValue));
            assertTrue(sql.parameters().get(1) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 2 && Integer.valueOf(2).equals(dbValue));
            assertTrue(sql.parameters().get(2) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 3 && Integer.valueOf(3).equals(dbValue));
        }, () -> {
            var orm = ORM(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .where(it -> it.where(RAW."\{alias(Owner.class, INNER)}.id = \{1}")
                            .and(it.where(RAW."EXISTS (\{it.subquery(Owner.class).where(RAW."\{alias(Owner.class, INNER)}.id = \{2}")})"))
                            .and(it.where(RAW."3 = \{3}")))
                    .getResultList();
        });
    }

    @Test
    public void testWhereAppendQueryBuilderParameters() {
        String expectedSql = """
            SELECT o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
            FROM owner o
            LEFT JOIN city c ON o.city_id = c.id
            WHERE o.id = ?
            AND EXISTS (
              SELECT o1.id, o1.first_name, o1.last_name, o1.address, o1.city_id, o1.telephone, o1.version
              FROM owner o1
              WHERE o1.id = ?
            )
            AND 3 = ?""";
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertTrue(sql.parameters().get(0) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 1 && Integer.valueOf(1).equals(dbValue));
            assertTrue(sql.parameters().get(1) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 2 && Integer.valueOf(2).equals(dbValue));
            assertTrue(sql.parameters().get(2) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 3 && Integer.valueOf(3).equals(dbValue));
        }, () -> {
            var orm = ORM(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .append(RAW."WHERE \{alias(Owner.class, INNER)}.id = \{1}")
                    .append(RAW."AND EXISTS (\{orm.subquery(Owner.class).where(RAW."\{alias(Owner.class, INNER)}.id = \{2}")})")
                    .append(RAW."AND 3 = \{3}")
                    .getResultList();
        });
    }

    /**
     * Simple business object representing a pet.
     */
    @Builder(toBuilder = true)
    @DbTable("pet")
    public record PetWithEnum(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull LocalDate birthDate,
            @Nonnull @DbEnum(ORDINAL) @DbColumn("type_id") PetTypeEnum type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Test
    public void testOrdinalEnumSelect() {
        var pets = ORM(dataSource).entity(PetWithEnum.class)
                .select()
                .getResultList();
        assertEquals(13, pets.size());
    }

    /**
     * Simple business object representing a pet.
     */
    @Builder(toBuilder = true)
    @DbTable("pet")
    public record PetWithIntEnum(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull LocalDate birthDate,
            @DbColumn("type_id") int type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Test
    public void testIntEnumSelect() {
        var pets = ORM(dataSource).entity(PetWithIntEnum.class)
                .select()
                .getResultList();
        assertEquals(13, pets.size());
    }

    @Test
    public void testSelectCompoundFK() {
        var repository = ORM(dataSource).entity(VisitWithCompoundFK.class);
        var visit = repository.getById(3);
        assertEquals(3, visit.vetSpecialty().vet().id());
        assertEquals(2, visit.vetSpecialty().specialty().id());
    }

    @Test
    public void testInsertCompoundFK() {
        var repository = ORM(dataSource).entity(VisitWithCompoundFK.class);
        VisitWithCompoundFK visit = new VisitWithCompoundFK(0, LocalDate.now(), "test", Pet.builder().id(1).build(), VetSpecialty.builder().id(new VetSpecialtyPK(3, 2)).build(), Instant.now());
        repository.insert(visit);
    }

    @Test
    public void testUpdateCompoundFK() {
        var repository = ORM(dataSource).entity(VisitWithCompoundFK.class);
        var visit = repository.getById(3);
        visit = visit.toBuilder().vetSpecialty(null).build();
        repository.update(visit);
        repository.getById(3);
        assertNull(visit.vetSpecialty());
    }

    @Test
    public void testSekectWhereCompoundFK() {
        var repository = ORM(dataSource).entity(VisitWithCompoundFK.class);
        var visit = repository.select().where(VisitWithCompoundFK_.vetSpecialty, VetSpecialty.builder().id(new VetSpecialtyPK(3, 2)).build()).getSingleResult();
        assertEquals(3, visit.vetSpecialty().vet().id());
        assertEquals(2, visit.vetSpecialty().specialty().id());
    }

    @DbTable("visit")
    public record VisitWithDbColumns(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Nullable @FK @DbColumn(name = "test1") @DbColumn(name = "test2") VetSpecialty vetSpecialty,
            @Version Instant timestamp
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectCompoundFKDbColumns() {
        String expectedSql = """
                SELECT vwdc.id, vwdc.visit_date, vwdc.description, p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version, vs.vet_id, vs.specialty_id, v.id, v.first_name, v.last_name, s.id, s.name, vwdc."timestamp"
                FROM visit vwdc
                INNER JOIN pet p ON vwdc.pet_id = p.id
                INNER JOIN pet_type pt ON p.type_id = pt.id
                LEFT JOIN owner o ON p.owner_id = o.id
                LEFT JOIN city c ON o.city_id = c.id
                LEFT JOIN vet_specialty vs ON vwdc.test1 = vs.vet_id AND vwdc.test2 = vs.specialty_id
                LEFT JOIN vet v ON vs.vet_id = v.id
                LEFT JOIN specialty s ON vs.specialty_id = s.id
                WHERE vwdc.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            var e = assertThrows(PersistenceException.class, () -> {
                var repository = ORM(dataSource).entity(VisitWithDbColumns.class);
                repository.getById(1);
            });
            assertInstanceOf(JdbcSQLSyntaxErrorException.class, e.getCause());
        });
    }

    @DbTable("vet_specialty")
    public record VetSpecialtyDbColumns(
            @Nonnull @PK(autoGenerated = false) @DbColumn(name = "test1") @DbColumn(name = "test2") VetSpecialtyPK id,  // Implicitly @Inlined
            @Nonnull @Persist(insertable = false) @FK("test3") Vet vet,
            @Nonnull @Persist(insertable = false) @FK("test4") Specialty specialty) implements Entity<VetSpecialtyPK> {
        public VetSpecialtyDbColumns(@Nonnull VetSpecialtyPK pk) {
            //noinspection DataFlowIssue
            this(pk, null, null);
        }
    }
    @DbTable("visit")
    public record VisitWithNestedDbColumns(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Nullable @FK VetSpecialtyDbColumns vetSpecialty,
            @Version Instant timestamp
    ) implements Entity<Integer> {
    }

    @Test
    public void testSelectCompoundFKNestedDbColumns() {
        String expectedSql = """
                SELECT vwndc.id, vwndc.visit_date, vwndc.description, p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version, vsdc.vet_id, vsdc.specialty_id, v.id, v.first_name, v.last_name, s.id, s.name, vwndc."timestamp"
                FROM visit vwndc
                INNER JOIN pet p ON vwndc.pet_id = p.id
                INNER JOIN pet_type pt ON p.type_id = pt.id
                LEFT JOIN owner o ON p.owner_id = o.id
                LEFT JOIN city c ON o.city_id = c.id
                LEFT JOIN vet_specialty vsdc ON vwndc.vet_id = vsdc.test1 AND vwndc.specialty_id = vsdc.test2
                LEFT JOIN vet v ON vsdc.test3 = v.id
                LEFT JOIN specialty s ON vsdc.test4 = s.id
                WHERE vwndc.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            var e = assertThrows(PersistenceException.class, () -> {
                var repository = ORM(dataSource).entity(VisitWithNestedDbColumns.class);
                repository.getById(1);
            });
            assertInstanceOf(JdbcSQLSyntaxErrorException.class, e.getCause());
        });
    }
}