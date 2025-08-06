package st.orm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.Pet;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.model.Pet_;
import st.orm.core.model.Specialty;
import st.orm.core.model.Vet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.Vet_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateBuilder;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.IN;
import static st.orm.Operator.NOT_EQUALS;
import static st.orm.Operator.NOT_IN;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class BuilderPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testBuilderWithJoin() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(raw("\0.id = \0.pet_id", Pet.class, Visit.class))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunction() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(TemplateBuilder.create(it -> "%s.id = %s.pet_id".formatted(it.insert(Pet.class), it.insert(Visit.class))))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinParameter() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(raw("\0.id = \0", Pet.class, 1))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunctionParameter() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(TemplateBuilder.create(it -> "%s.id = %d".formatted(it.insert(Pet.class), 1)))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunctionParameterMetamodel() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(TemplateBuilder.create(it -> "%s.id = %d".formatted(it.insert(Pet.class), 1)))
                .where(raw("\0 = \0", Visit_.visitDate, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(Pet.class)
                .where(it -> it.whereAny(Visit.builder().id(1).build()))
                .getResultList();
         assertEquals(1, list.size());
         assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Pet.class)
                    .innerJoin(Visit.class).on(Pet.class)
                    .where(it -> it.whereAny(Vet.builder().id(1).build()))
                    .getResultStream()
                    .count();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithCustomSelect() {
        record Result(Pet pet, int visitCount) {}
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class, Result.class, raw("\0, COUNT(*)", Pet.class))
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithCompoundPkJoin() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithDoubleCompoundPkJoin() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithMultipleWhere() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .typed(Integer.class)
                    .where(1)
                    .where(2)
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhere() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.whereId(1).or(it.whereId(2)))
                .getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereNullRef() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .where(Ref.ofNull())
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhereEmpty() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyEquals() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .where(Vet_.id, EQUALS, List.of())
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhereEmptyIn() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.id, IN, List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyNotEquals() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .where(Vet_.id, NOT_EQUALS, List.of())
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhereEmptyNotIn() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.id, NOT_IN, List.of())
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplate() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.whereId(1).or(it.where(raw("\0.id = 2", Vet.class))))
                .getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunction() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(it -> it.where(TemplateBuilder.create(ignore -> "1 = 1")))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunctionAfterOr() {
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.whereId(1).or(
                        it.where(TemplateBuilder.create(i -> "%s.id = %s".formatted(i.insert(Vet.class), i.insert(2))))))
                .getResultList();
        assertEquals(2, list.size());
    }
}