package st.orm.core;

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

import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.DbColumn;
import st.orm.DbEnum;
import st.orm.DbTable;
import st.orm.DefaultJoinType;
import st.orm.Entity;
import st.orm.FK;
import st.orm.Metamodel;
import st.orm.OptimisticLockException;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.SelectMode;
import st.orm.Version;
import st.orm.core.model.Owner_;
import st.orm.core.model.Pet;
import st.orm.core.model.PetOwnerRecursion;
import st.orm.core.model.PetOwnerRef;
import st.orm.core.model.PetOwnerRef_;
import st.orm.core.model.PetTypeEnum;
import st.orm.core.model.PetWithNullableOwnerRef;
import st.orm.core.model.PetWithNullableOwnerRef_;
import st.orm.core.model.Pet_;
import st.orm.core.model.Specialty;
import st.orm.core.model.Vet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.Visit;
import st.orm.core.model.VisitWithCompoundFK;
import st.orm.core.model.VisitWithCompoundFK_;
import st.orm.core.model.VisitWithTwoPetRefs;
import st.orm.core.model.VisitWithTwoPets;
import st.orm.core.model.VisitWithTwoPetsOneRef;
import st.orm.core.model.VisitWithTwoPetsOneRef_;
import st.orm.core.model.VisitWithTwoPets_;
import st.orm.core.model.Visit_;
import st.orm.core.repository.PetRepository;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Sql;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateBuilder;
import st.orm.core.template.TemplateString;

import javax.sql.DataSource;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.core.template.Templates.alias;
import static st.orm.core.template.Templates.column;
import static st.orm.core.template.Templates.select;
import static st.orm.core.template.Templates.where;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.EnumType.ORDINAL;
import static st.orm.Operator.BETWEEN;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN;
import static st.orm.Operator.GREATER_THAN_OR_EQUAL;
import static st.orm.Operator.IN;
import static st.orm.Operator.IS_NULL;
import static st.orm.ResolveScope.INNER;
import static st.orm.ResolveScope.OUTER;
import static st.orm.core.template.TemplateString.wrap;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RepositoryPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelect() {
        assertEquals(10, ORMTemplate.of(dataSource).entity(Owner.class).selectCount().getSingleResult());
    }

    @Test
    public void testCount() {
        assertEquals(10, ORMTemplate.of(dataSource).entity(Owner.class).count());
    }

    @Test
    public void testResultCount() {
        assertEquals(10, ORMTemplate.of(dataSource).entity(Owner.class).select().getResultCount());
    }

    @Test
    public void testSelectByFk() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByFkNested() {
        assertEquals(2, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.pet.owner, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumn() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.visitDate, EQUALS, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThan() {
        assertEquals(13, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.visitDate, GREATER_THAN, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnGreaterThanOrEqual() {
        assertEquals(14, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.visitDate, GREATER_THAN_OR_EQUAL, LocalDate.of(2023, 1, 1)).getResultCount());
    }

    @Test
    public void testSelectByColumnBetween() {
        assertEquals(10, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.visitDate, BETWEEN, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 9)).getResultCount());
    }

    @Test
    public void testSelectByColumnIsNull() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner, IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByColumnRefIsNull() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class).select().where(PetWithNullableOwnerRef_.owner, IS_NULL).getResultCount());
    }

    @Test
    public void testSelectByColumnEqualsNull() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner, EQUALS, (Owner) null).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectByColumnRefEqualsNull() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class).select().where(PetWithNullableOwnerRef_.owner, EQUALS, (Owner) null).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectByColumnEqualsId() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner.id, EQUALS, 1).getResultCount());
    }

    @Test
    public void testSelectByColumnRecord() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner, EQUALS, Owner.builder().id(1).build()).getResultCount());
    }

    @Test
    public void testSelectByColumnRef() {
        assertEquals(1, ORMTemplate.of(dataSource).entity(Pet.class).select().where(Pet_.owner, Ref.of(Owner.builder().id(1).build())).getResultCount());
    }

    @Test
    public void testSelectByNestedColumn() {
        assertEquals(2, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.pet.name, EQUALS, "Leo").getResultCount());
    }

    @Test
    public void testSelectByDeeperNestedColumn() {
        assertEquals(2, ORMTemplate.of(dataSource).entity(Visit.class).select().where(Visit_.pet.owner.firstName, EQUALS, "Betty").getResultCount());
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
            ORMTemplate.of(dataSource).entity(Visit.class).select().where(wrap(where(invalidModel, EQUALS, "Leo"))).getResultCount();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectCountCustomClass() {
        record Count(Owner owner, int value) {}
        var query = ORMTemplate.of(dataSource).query(raw("SELECT \0, COUNT(*) FROM \0 GROUP BY \0", Owner.class, Pet.class, Owner_.id));
        var result = query.getResultList(Count.class);
        assertEquals(10, result.size());
        assertEquals(12, result.stream().mapToInt(Count::value).sum());
    }

    @Test
    public void testInsert() {
        var repository = ORMTemplate.of(dataSource).entity(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        repository.insert(List.of(vet1, vet2));
        var list = repository.select().getResultList();
        assertEquals(8, list.size());
        assertEquals("Scarlett", list.getLast().firstName());
    }

    @Test
    public void testInsertReturningIds() {
        var repository = ORMTemplate.of(dataSource).entity(Vet.class);
        Vet vet1 = Vet.builder().firstName("Noel").lastName("Fitzpatrick").build();
        Vet vet2 = Vet.builder().firstName("Scarlett").lastName("Magda").build();
        var ids = repository.insertAndFetchIds(List.of(vet1, vet2));
        assertEquals(List.of(7, 8), ids);
    }

    @Test
    public void testInsertReturningIdsCompoundPk() {
        try {
            var repository = ORMTemplate.of(dataSource).entity(VetSpecialty.class);
            VetSpecialty vetSpecialty = VetSpecialty.builder().id(new VetSpecialtyPK(1, 1)).build();
            var id = repository.insertAndFetchId(vetSpecialty);
            assertEquals(1, id.vetId());
            assertEquals(1, id.specialtyId());
        } catch (PersistenceException ignore) {
            // May happen in case of Mysql/MariaDB as they only support auto increment columns.
        }
    }

    @Test
    public void testUpdateList() {
        var repository = ORMTemplate.of(dataSource).entity(Vet.class);
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
        var repository = ORMTemplate.of(dataSource).entity(Vet.class);
        var list = repository.select().getResultList();
        var list2 = repository.findAllById(list.stream().map(Vet::id).toList());
        assertEquals(list, list2);
    }

    @Test
    public void testSelectFetchRef() {
        var visit = ORMTemplate.of(dataSource).entity(VisitWithNullablePetRef.class).getById(1);
        //noinspection DataFlowIssue
        var pet = visit.pet().fetch();
        assertNotNull(visit.pet());
        //noinspection DataFlowIssue
        assertEquals("Jean", pet.owner().firstName());
        assertSame(pet, visit.pet().fetch());
    }

    @Test
    public void testSelectRef() {
        var visits = ORMTemplate.of(dataSource).entity(Visit.class).selectRef().getResultList();
        assertEquals(14, visits.size());
    }

    record VisitResult(Ref<Visit> visit) {}

    @Test
    public void testSelectRefTemplate() {
        var visits = ORMTemplate.of(dataSource).selectFrom(Visit.class, VisitResult.class, wrap(select(Visit.class, SelectMode.PK))).getResultList();
        assertEquals(14, visits.size());
    }

    @Test
    public void testSelectRefCustomType() {
        var pets = ORMTemplate.of(dataSource).entity(Visit.class).selectRef(Pet.class).getResultList();
        assertEquals(14, pets.size());
    }

    @Test
    public void testSelectWhereRef() {
        var owners = ORMTemplate.of(dataSource).entity(Owner.class).select().where(Ref.of(Owner.builder().id(1).build())).getResultList();
        assertEquals(1, owners.size());
        assertEquals(1, owners.getFirst().id());
    }

    @Test
    public void testSelectRefCompoundPk() {
        var vetSpecialties = ORMTemplate.of(dataSource).entity(VetSpecialty.class).selectRef().getResultList();
        assertEquals(5, vetSpecialties.size());
    }

    @Test
    public void testSelectWhereRefCompoundPk() {
        var vetSpecialties = ORMTemplate.of(dataSource).entity(VetSpecialty.class).select().where(Ref.of(VetSpecialty.builder().id(new VetSpecialtyPK(2, 1)).build())).getResultList();
        assertEquals(1, vetSpecialties.size());
        assertEquals(2, vetSpecialties.getFirst().vet().id());
        assertEquals(1, vetSpecialties.getFirst().specialty().id());
    }

    @Test
    public void testSelectWithInvalidPath() {
        var e = assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource).entity(Pet.class).select()
                        .where(wrap(where(Metamodel.of(Pet.class, "owner.city"), EQUALS, "Sunnyvale")))
                        .getResultList());
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testWhereWithOperatorAndRecord() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).getById(1);
            ORMTemplate.of(dataSource).entity(Pet.class).select()
                    .where(raw("\0 = \0", Pet_.owner, owner))
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInvalidPathNoArg() {
        var e = assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource).entity(Pet.class).select()
                        .where(wrap(where(Metamodel.of(Pet.class, "owner.city"), IS_NULL)))
                        .getResultList());
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testInsertPetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORMTemplate.of(dataSource).entity(Visit.class);
            //noinspection DataFlowIssue
            Visit visit = new Visit(0, LocalDate.now(), "test", null, Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORMTemplate.of(dataSource).entity(Visit.class);
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
            var repository = ORMTemplate.of(dataSource).entity(VisitWithNonnullPetRef.class);
            VisitWithNonnullPetRef visit = new VisitWithNonnullPetRef(0, LocalDate.now(), "test", null);
            repository.insert(visit);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    void testUpdatePetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORMTemplate.of(dataSource).entity(VisitWithNonnullPetRef.class);
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
            var repository = ORMTemplate.of(dataSource).entity(VisitWithNullablePetRef.class);
            VisitWithNullablePetRef visit = new VisitWithNullablePetRef(0, LocalDate.now(), "test", null, Instant.now());
            repository.insert(visit);
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testUpdateNullablePetRefWithNull() {
        var e = assertThrows(PersistenceException.class, () -> {
            var repository = ORMTemplate.of(dataSource).entity(VisitWithNullablePetRef.class);
            var visit = repository.getById(1);
            repository.update(visit.toBuilder().pet(null).build());
        });
        assertInstanceOf(SQLIntegrityConstraintViolationException.class, e.getCause());
    }

    @Test
    void testInsertNullablePetRefWithNonnull() {
        var visitRepository = ORMTemplate.of(dataSource).entity(VisitWithNullablePetRef.class);
        VisitWithNullablePetRef visit = new VisitWithNullablePetRef(0, LocalDate.now(), "test", Ref.of(Pet.builder().id(1).build()), Instant.now());
        var id = visitRepository.insertAndFetchId(visit);
        var visitFromDb = visitRepository.getById(id);
        assertEquals(id, visitFromDb.id());
        //noinspection DataFlowIssue
        assertNotNull(visitFromDb.pet());
        assertEquals(1, visitFromDb.pet().fetch().id());
    }

    @Test
    public void testSelectNullOwnerRef() {
        try (var stream = ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class).selectAll()) {
            var pet = stream.filter(p -> p.name().equals("Sly")).findFirst().orElseThrow();
            assertNull(pet.owner());
        }
    }

    @Test
    public void testSelectRefInnerJoin() {
        // Ref elements are not joined by default. Test whether join works.
        var owners = ORMTemplate.of(dataSource)
                .selectFrom(PetWithNullableOwnerRef.class, Owner.class, raw("DISTINCT \0", Owner.class))
                .innerJoin(Owner.class).on(PetWithNullableOwnerRef.class)
                .innerJoin(City.class).on(Owner.class)
                .getResultList();
        assertEquals(10, owners.size());
    }

    @Test
    public void testSelectWithWrapper() {
        record Wrapper(Pet pet) {}
        var pets = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Wrapper.class)
                .getResultList();
        assertEquals(13, pets.size());
    }

    @Test
    public void testSelectWithWrapperNullOwner() {
        record Wrapper(Pet pet) {}
        var wrapper = ORMTemplate.of(dataSource)
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
        var wrapper = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Wrapper.class)
                .where(raw("\0.id = \0", Pet.class, 13))
                .getSingleResult();
        assertEquals(13, wrapper.pet().id());
        assertNull(wrapper.pet().owner().owner());
    }

    @Test
    public void testSelectWithTwoPetsWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).selectFrom(Owner.class).getResultList().getFirst();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class).select().where(it -> it.whereAny(owner)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithExpression() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(raw("\0.owner_id = \0", PetOwnerRef.class, owner.id()))).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithExpressionMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0", PetOwnerRef_.owner, owner.id()))).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithWhere() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(raw("\0.owner_id = \0", PetOwnerRef.class, owner.id())).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithWhereMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().getResultList().getFirst();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(raw("\0 = \0", PetOwnerRef_.owner, owner.id())).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsWithPathRaw() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        var list = ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class).select().append(raw("WHERE \0", where(VisitWithTwoPets_.pet1.owner, EQUALS, owner))).getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testSelectWithTwoPetsWithPathTemplate() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                .select()
                .where(it -> it.where(raw("\0 = \0", VisitWithTwoPets_.pet1.owner, owner.id()))).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsWithMultipleParameters() {
        var orm = ORMTemplate.of(dataSource);
        var owner = orm.entity(Owner.class).select().append("LIMIT 1").getSingleResult();
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
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPets.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0 OR \0 = \0", VisitWithTwoPets_.pet1.owner, owner.id(), VisitWithTwoPets_.pet2.owner, owner.id()))).getResultList();
            assertEquals(2, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testPetOwnerRecursion() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource).entity(PetOwnerRecursion.class).select().getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithoutPath() throws Exception {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(it -> it.whereAny(owner)).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithoutPathTemplate() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(raw("\0.owner_id = \0", PetOwnerRef.class, owner.id()))).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithRootPathTemplateMetamodel() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var list = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0", PetOwnerRef_.owner, owner.id()))).getResultList();
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
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(it -> it.where(raw("\0 = \0", Pet_.owner, owner.id()))).getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithInvalidPathMetamodel() {
        var e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(it -> it.whereAny(PetOwnerRef_.owner, owner)).getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithRootPathMetamodelTemplate() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var list = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(raw("\0 = \0", PetOwnerRef_.owner, owner.id())).getResultList();
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
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
            AtomicReference<Sql> sql = new AtomicReference<>();
            observe(sql::setPlain, () -> {
                ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                        .select()
                        .where(raw("\0 = \0", Pet_.owner, owner.id())).getResultList();
            });
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithPath() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet1.owner, EQUALS, owner).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefWithPathTemplate() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0", VisitWithTwoPetsOneRef_.pet1.owner, owner.id()))).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithoutPath() {
        var e = assertThrows(PersistenceException.class, () -> {
            var pet = ORMTemplate.of(dataSource).entity(PetOwnerRef.class).select().append("LIMIT 1").getSingleResult();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(it -> it.whereAny(pet)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithoutPathTemplate() {
        // Note that this test is not comparable to the previous test because of the re-use of pet_id.
        var pet = ORMTemplate.of(dataSource).entity(PetOwnerRef.class).select().append("LIMIT 1").getSingleResult();
        var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                .select()
                .where(it -> it.where(raw("\0.id = \0", PetOwnerRef.class, pet.id()))).getResultList();
        assertEquals(2, visits.size());
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithPath() {
        var pet = ORMTemplate.of(dataSource).entity(PetOwnerRef.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet1, EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefPetWithPathTemplateMetamodel() throws Exception {
        var ORM = ORMTemplate.of(dataSource);
        var pet = ORM.entity(PetOwnerRef.class).select().append("LIMIT 1").getSingleResult();
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORM.entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0", VisitWithTwoPetsOneRef_.pet1, pet.id()))).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefOtherPetWithPath() {
        var pet = ORMTemplate.of(dataSource).entity(PetOwnerRef.class).getById(1);
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class).select().where(VisitWithTwoPetsOneRef_.pet2, EQUALS, pet).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetsOneRefOtherPetWithPathTemplateMetamodel() {
        AtomicReference<Sql> sql = new AtomicReference<>();
        observe(sql::setPlain, () -> {
            var visits = ORMTemplate.of(dataSource).entity(VisitWithTwoPetsOneRef.class)
                    .select()
                    .where(it -> it.where(raw("\0 = \0", VisitWithTwoPetsOneRef_.pet2, 1))).getResultList();
            assertEquals(1, sql.getPlain().parameters().size());
            assertEquals(2, visits.size());
        });
    }

    @Test
    public void testSelectWithTwoPetRefsWithoutPath() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPetRefs.class).select().where(it -> it.whereAny(owner)).getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithTwoPetRefsWithoutPathTemplate() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var owner = ORMTemplate.of(dataSource).entity(Owner.class).select().append("LIMIT 1").getSingleResult();
            ORMTemplate.of(dataSource).entity(VisitWithTwoPetRefs.class)
                    .select()
                    .where(it -> it.where(raw("\0.id = \0", PetOwnerRef.class, owner.id()))) // Cannot find PetOwnerRef alias as ref fields are not joined.
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testInternerRecord() {
        var pets = ORMTemplate.of(dataSource).entity(Pet.class).select().getResultList();
        var owners = Collections.newSetFromMap(new IdentityHashMap<>());
        owners.addAll(pets.stream().map(Pet::owner).toList());
        assertEquals(11, owners.size());
    }

    @Test
    public void testInternerRef() {
        var pets = ORMTemplate.of(dataSource).entity(PetOwnerRef.class).select().getResultList();
        var owners = Collections.newSetFromMap(new IdentityHashMap<>());
        owners.addAll(pets.stream().map(PetOwnerRef::owner).toList());
        assertEquals(11, owners.size());
    }

    @Test
    public void testSelectWithInlinePath() {
        var list = ORMTemplate.of(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, EQUALS, "Madison").getResultList();
        assertEquals(4, list.size());
    }

    @Test
    public void testSelectWithInlinePathEqualsMultiArgument() {
        var e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, EQUALS, "Madison", "Monona").getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWithInlinePathInMultiArgument() {
        var list = ORMTemplate.of(dataSource).entity(Owner.class).select().where(Owner_.address.city.name, IN, "Madison", "Monona").getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testSelectWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORMTemplate.of(dataSource).entity(Pet.class)
                .select()
                .where(it -> it.whereAny(owner))
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectRefWhere() {
        Owner owner = Owner.builder().id(1).build();
        var pets = ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class)
                .select()
                .where(it -> it.whereAny(owner))
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectRefWhereWithJoin() {
        var e = assertThrows(PersistenceException.class, () -> {
            Owner owner = Owner.builder().id(1).build();
            ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class)
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
        var pets = ORMTemplate.of(dataSource).entity(PetWithNullableOwnerRef.class)
                .select()
                .innerJoin(Owner.class).on(PetWithNullableOwnerRef.class)
                .where(PetWithNullableOwnerRef_.owner, EQUALS, owner)
                .getResultList();
        assertEquals(1, pets.size());
    }

    @Test
    public void testSelectNullableOwner() {
        // Ref elements are not joined by default. Test whether join works.
        var owners = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Owner.class, wrap(Owner.class))
                .getResultList();
        assertEquals(12, owners.size());
    }

    @Test
    public void testCustomRepo1() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById1(1));
    }

    @Test
    public void testCustomRepo2() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById2(1));
    }

    @Test
    public void testCustomRepo3() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        var pet = repo.getById(1);
        assertEquals(pet, repo.getById3(1));
    }

    @Test
    public void testCustomRepo4() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        assertEquals(1, repo.findByOwnerFirstName("Betty").size());
    }

    @Test
    public void testCustomRepo5() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        assertEquals(4, repo.findByOwnerCity("Madison").size());
    }

    @Test
    public void testPetVisitCount() {
        var repo = ORMTemplate.of(dataSource).repository(PetRepository.class);
        assertEquals(8, repo.petVisitCount().size());
    }

    @Test
    public void delete() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        repo.delete(Visit.builder().id(1).build());
        assertEquals(13, repo.select().getResultCount());
    }

    @Test
    public void deleteByPet() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        repo.delete().where(it -> it.whereAny(Pet.builder().id(1).build())).executeUpdate();
        assertEquals(12, repo.select().getResultCount());
    }

    @Test
    public void deleteByOwner() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        repo.delete().where(it -> it.whereAny(Owner.builder().id(1).build())).executeUpdate();
        assertEquals(12, repo.select().getResultCount());
    }

    @Test
    public void deleteAll() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        repo.deleteAll();
        assertEquals(0, repo.select().getResultCount());
    }

    @Test
    public void deleteBatch() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        try (var stream = repo.selectAll()) {
            repo.delete(stream);
        }
        assertEquals(0, repo.count());
    }

    @Test
    public void deleteRefBatch() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        try (var stream = repo.selectAll().map(Ref::of)) {
            repo.deleteByRef(stream);
        }
        assertEquals(0, repo.count());
    }

    @Test
    public void testBuilder() {
        var list = ORMTemplate.of(dataSource).entity(Pet.class)
                .select()
                .where(wrap(Pet.builder().id(1).build()))
                .getResultList();
        assertEquals(1, list.size());
    }

    @Test
    public void testBuilderWithJoin() {
        var list = ORMTemplate.of(dataSource)
                .entity(Pet.class)
                .select()
                .innerJoin(Visit.class).on(raw("\0.id = \0.pet_id", alias(Pet.class), Visit.class))
                .where(raw("\0.visit_date = \0", alias(Visit.class), LocalDate.of(2023, 1, 8)))
                .getResultList();
        assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var list = ORMTemplate.of(dataSource)
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
        var list = ORMTemplate.of(dataSource)
                .entity(Pet.class)
                .select()
                .innerJoin(Visit.class).on(Pet.class)
                .innerJoin(raw("SELECT * FROM \0", Pet.class), "x").on(raw("\0.id = x.id", Pet.class))    // Join just for the sake of testing multiple joins.
                .where(it -> it.whereAny(Visit.builder().id(1).build()))
                .getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
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
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Result.class, raw("\0, COUNT(*)", Pet_.id))
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithNonRecordSelectTemplate() {
        var count = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Integer.class, TemplateString.of("COUNT(*)"))
                .innerJoin(Visit.class).on(Pet.class)
                .getSingleResult();
        assertEquals(14, count);
    }

    @Test
    public void testBuilderWithCustomJoin() {
        record Result(int petId, int visitCount) {}
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Result.class, raw("\0, COUNT(*)", Pet_.id))
                .join(DefaultJoinType.INNER, raw("SELECT * FROM \0 WHERE \0.id > \0", Visit.class, Visit.class, -1), "x")
                .on(raw("\0.id = x.pet_id", Pet.class))
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithSubqueryJoin() {
        record Result(int petId, int visitCount) {}
        var orm = ORMTemplate.of(dataSource);
        var list = orm
                .selectFrom(Pet.class, Result.class, raw("\0, COUNT(*)", Pet_.id))
                .join(DefaultJoinType.INNER, orm.subquery(Visit.class), "x").on(raw("\0 = x.pet_id", Pet_.id))
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testWithArg() {
        var list = ORMTemplate.of(dataSource).entity(Pet.class).select().where(raw("\0.id = 7", Pet.class)).getResultList();
        assertEquals(1, list.size());
        assertEquals(7, list.getFirst().id());
        ORMTemplate.of(dataSource).entity(Pet.class).countById(list.stream().map(Pet::id));
    }

    @Test
    public void testWithTwoArgs() {
        var ORM = ORMTemplate.of(dataSource);
        var list = ORM.entity(Pet.class).select().where(TemplateBuilder.create(it -> "%s.id = 7 OR %s.id = 8".formatted(it.insert(Pet.class), it.insert(Pet.class)))).getResultList();
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
        var repo = ORMTemplate.of(dataSource).entity(Owner.class);
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            repo.select().forUpdate().where(1).getSingleResult();
        });
    }

    @Test
    public void updateOwnerIntegerVersion() {
        var repo = ORMTemplate.of(dataSource).entity(Owner.class);
        Owner owner = repo.getById(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        Owner updatedOwner = repo.updateAndFetch(modifiedOwner);
        repo.update(updatedOwner);
    }

    @Test
    public void testUpdateOwnerWrongIntegerVersion() {
        var repo = ORMTemplate.of(dataSource).entity(Owner.class);
        Owner owner = repo.getById(1);
        Owner modifiedOwner = owner.toBuilder().address(owner.address().toBuilder().address("Test Street").build()).build();
        repo.update(modifiedOwner);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedOwner));
    }

    @Test
    public void testUpdateVisitTimestampVersion() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        Visit updatedVisit = repo.updateAndFetch(modifiedVisit);
        repo.update(updatedVisit);
    }

    @Test
    public void testUpdateVisitWrongTimestampVersion() {
        var repo = ORMTemplate.of(dataSource).entity(Visit.class);
        Visit visit = repo.select().getResultList().getFirst();
        Visit modifiedVisit = visit.toBuilder().visitDate(LocalDate.now()).build();
        repo.update(modifiedVisit);
        assertThrows(OptimisticLockException.class, () -> repo.update(modifiedVisit));
    }

    @Test
    public void testWhereExists() {
        var list = ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(raw("\0.id = \0.id", alias(Owner.class, OUTER), alias(Owner.class, INNER)))))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsMetamodel() {
        var list = ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(raw("\0 = \0", column(Owner_.id, OUTER), column(Owner_.id, INNER)))))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsCascade() {
        // The Owner.id = Owner.id is not ambiguous because they are in different scopes.
        ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .where(it -> it.exists(it.subquery(Visit.class).where(raw("\0.id = \0.id", Owner.class, Owner.class))))
                .getResultList();
    }

    @Test
    public void testWhereExistsPredicateCascade() {
        // The Owner.id = Owner.id is not ambiguous because they are in different scopes.
        ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .where(it ->
                        it.where(raw("EXISTS (\0)", it.subquery(Visit.class).where(raw("\0.id = \0.id", Owner.class, Owner.class)))))
                .getResultList();
    }

    @Test
    public void testWhereExistsPredicate() {
        var list = ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .where(it ->
                        it.where(raw("EXISTS (\0)", it.subquery(Visit.class).where(raw("\0.id = \0.id", alias(Owner.class, OUTER), alias(Owner.class, INNER))))))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereExistsAppendSubquery() {
        var orm = ORMTemplate.of(dataSource);
        var list = ORMTemplate.of(dataSource).entity(Owner.class)
                .select()
                .append(raw("WHERE EXISTS (\0)", orm.subquery(Visit.class).where(raw("\0.id = \0.id", alias(Owner.class, OUTER), alias(Owner.class, INNER)))))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testWhereAppendQuery() {
        // We cannot append a query, we need to use a query builder instead.
        var e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource).entity(Owner.class)
                    .select()
                    .append(raw("WHERE (\0)", ORMTemplate.of(dataSource).query("SELECT 1")))
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
            WHERE (o.id = ?) AND (EXISTS (
              SELECT o1.id, o1.first_name, o1.last_name, o1.address, o1.city_id, o1.telephone, o1.version
              FROM owner o1
              WHERE o1.id = ?
            )) AND (3 = ?)""";
        observe(sql -> {
            assertEquals(expectedSql, sql.statement());
            assertTrue(sql.parameters().get(0) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 1 && Integer.valueOf(1).equals(dbValue));
            assertTrue(sql.parameters().get(1) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 2 && Integer.valueOf(2).equals(dbValue));
            assertTrue(sql.parameters().get(2) instanceof PositionalParameter(int position, Object dbValue)
                    && position == 3 && Integer.valueOf(3).equals(dbValue));
        }, () -> {
            var orm = ORMTemplate.of(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .where(it -> it.where(raw("\0.id = \0", alias(Owner.class, INNER), 1))
                            .and(it.where(raw("EXISTS (\0)", it.subquery(Owner.class).where(raw("\0.id = \0", alias(Owner.class, INNER), 2)))))
                            .and(it.where(raw("3 = \0", 3))))
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
            var orm = ORMTemplate.of(dataSource);
            orm.entity(Owner.class)
                    .select()
                    .append(raw("WHERE \0.id = \0", alias(Owner.class, INNER), 1))
                    .append(raw("AND EXISTS (\0)", orm.subquery(Owner.class).where(raw("\0.id = \0", alias(Owner.class, INNER), 2))))
                    .append(raw("AND 3 = \0", 3))
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
        var pets = ORMTemplate.of(dataSource).entity(PetWithEnum.class)
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
        var pets = ORMTemplate.of(dataSource).entity(PetWithIntEnum.class)
                .select()
                .getResultList();
        assertEquals(13, pets.size());
    }

    @Test
    public void testSelectCompoundFK() {
        var repository = ORMTemplate.of(dataSource).entity(VisitWithCompoundFK.class);
        var visit = repository.getById(3);
        assertEquals(3, visit.vetSpecialty().vet().id());
        assertEquals(2, visit.vetSpecialty().specialty().id());
    }

    @Test
    public void testInsertCompoundFK() {
        var repository = ORMTemplate.of(dataSource).entity(VisitWithCompoundFK.class);
        VisitWithCompoundFK visit = new VisitWithCompoundFK(0, LocalDate.now(), "test", Pet.builder().id(1).build(), VetSpecialty.builder().id(new VetSpecialtyPK(3, 2)).build(), Instant.now());
        repository.insert(visit);
    }

    @Test
    public void testUpdateCompoundFK() {
        var repository = ORMTemplate.of(dataSource).entity(VisitWithCompoundFK.class);
        var visit = repository.getById(3);
        visit = visit.toBuilder().vetSpecialty(null).build();
        repository.update(visit);
        repository.getById(3);
        assertNull(visit.vetSpecialty());
    }

    @Test
    public void testSekectWhereCompoundFK() {
        var repository = ORMTemplate.of(dataSource).entity(VisitWithCompoundFK.class);
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
                var repository = ORMTemplate.of(dataSource).entity(VisitWithDbColumns.class);
                repository.getById(1);
            });
            assertInstanceOf(JdbcSQLSyntaxErrorException.class, e.getCause());
        });
    }

    @DbTable("vet_specialty")
    public record VetSpecialtyDbColumns(
            @Nonnull @PK(generation = NONE) @DbColumn(name = "test1") @DbColumn(name = "test2") VetSpecialtyPK id,// Implicitly @Inlined
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
                var repository = ORMTemplate.of(dataSource).entity(VisitWithNestedDbColumns.class);
                repository.getById(1);
            });
            assertInstanceOf(JdbcSQLSyntaxErrorException.class, e.getCause());
        });
    }
}