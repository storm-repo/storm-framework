package st.orm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Pet;
import st.orm.model.Pet_;
import st.orm.model.Specialty;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.Vet_;
import st.orm.model.Visit;
import st.orm.model.Visit_;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Templates.ORM;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.Operator.IN;
import static st.orm.template.Operator.NOT_EQUALS;
import static st.orm.template.Operator.NOT_IN;
import static st.orm.template.TemplateFunction.template;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class BuilderPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testBuilderWithJoin() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(RAW."\{Pet.class}.id = \{Visit.class}.pet_id")
                .where(RAW."\{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunction() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(template(it -> STR."\{it.invoke(Pet.class)}.id = \{it.invoke(Visit.class)}.pet_id"))
                .where(RAW."\{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinParameter() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(RAW."\{Pet.class}.id = \{1}")
                .where(RAW."\{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunctionParameter() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(template(it -> STR."\{it.invoke(Pet.class)}.id = \{1}"))
                .where(RAW."\{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunctionParameterMetamodel() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(template(it -> STR."\{it.invoke(Pet.class)}.id = \{1}"))
                .where(RAW."\{Visit_.visitDate} = \{LocalDate.of(2023, 1, 8)}")
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var list = ORM(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(Pet.class)
                .whereAny(Visit.builder().id(1).build())
                .getResultList();
         assertEquals(1, list.size());
         assertEquals(7, list.getFirst().id());
    }

    @Test
    public void testBuilderWithAutoJoinInvalidType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
                    .selectFrom(Pet.class)
                    .innerJoin(Visit.class).on(Pet.class)
                    .whereAny(Vet.builder().id(1).build())
                    .getResultStream()
                    .count();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithCustomSelect() {
        record Result(Pet pet, int visitCount) {}
        var list = ORM(dataSource)
                .selectFrom(Pet.class, Result.class, RAW."\{Pet.class}, COUNT(*)")
                .innerJoin(Visit.class).on(Pet.class)
                .groupBy(Pet_.id)
                .getResultList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithCompoundPkJoin() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithDoubleCompoundPkJoin() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithMultipleWhere() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
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
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.filter(1).or(it.filter(2)))
                .getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereEmpty() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .where(List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyEquals() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
                    .selectFrom(Vet.class)
                    .where(Vet_.id, EQUALS, List.of())
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhereEmptyIn() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.id, IN, List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyNotEquals() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource)
                    .selectFrom(Vet.class)
                    .where(Vet_.id, NOT_EQUALS, List.of())
                    .getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhereEmptyNotIn() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.id, NOT_IN, List.of())
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplate() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.filter(1).or(it.expression(RAW."\{Vet.class}.id = 2")))
                .getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunction() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .where(it -> it.expression(template(_ -> "1 = 1")))
                .getResultList();
        assertEquals(6, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunctionAfterOr() {
        var list = ORM(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.filter(1).or(
                        it.expression(template(ctx -> STR."\{ctx.invoke(Vet.class)}.id = \{ctx.invoke(2)}"))))
                .getResultList();
        assertEquals(2, list.size());
    }
}