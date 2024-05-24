package st.orm;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Pet;
import st.orm.model.Specialty;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.Visit;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Templates.ORM;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class BuilderPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testBuilderWithJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
                .innerJoin(Visit.class).on()."\{Pet.class}.id = \{Visit.class}.pet_id"
                ."WHERE \{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}"
                .toList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunction() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
                .innerJoin(Visit.class).on().template(it -> STR."\{it.arg(Pet.class)}.id = \{it.arg(Visit.class)}.pet_id")
                ."WHERE \{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}"
                .toList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinParameter() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
                .innerJoin(Visit.class).on()."\{Pet.class}.id = \{1}"
                ."WHERE \{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}"
                .toList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunctionParameter() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
                .innerJoin(Visit.class).on().template(it -> STR."\{it.arg(Pet.class)}.id = \{1}")
                ."WHERE \{Visit.class}.visit_date = \{LocalDate.of(2023, 1, 8)}"
                .toList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithAutoJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
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
            ORM.query(Pet.class)
                    .innerJoin(Visit.class).on(Pet.class)
                    .where(Vet.builder().id(1).build())
                    .stream()
                    .count();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithCustomSelect() {
        record Result(Pet pet, int visitCount) {}
        var ORM = ORM(dataSource);
        var list = ORM.query(Pet.class)
                .selectTemplate(Result.class)."\{Pet.class}, COUNT(*)"
                .innerJoin(Visit.class).on(Pet.class)
                ."GROUP BY \{Pet.class}.id"
                .toList();
        assertEquals(8, list.size());
        assertEquals(14, list.stream().mapToInt(Result::visitCount).sum());
    }

    @Test
    public void testBuilderWithCompoundPkJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .stream()
                .toList();
        System.out.println(list);
    }

    @Test
    public void testBuilderWithDoubleCompoundPkJoin() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .stream()
                .toList();
        System.out.println(list);
    }

    @Test
    public void testBuilderWithMultipleWhere() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            ORM.query(Vet.class)
                    .where(1)
                    .where(2).toList();

        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testBuilderWithWhere() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .where(it -> it.matches(1).or(it.matches(2)))
                .toList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplate() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .where(it -> it.matches(1).or(it."\{Vet.class}.id = 2"))
                .toList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunction() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .where(it -> it.template(_ -> "1 = 1"))
                .toList();
        assertEquals(6, list.size());
    }

    @Test
    public void testBuilderWithWhereTemplateFunctionAfterOr() {
        var ORM = ORM(dataSource);
        var list = ORM.query(Vet.class)
                .where(it -> it.matches(1).or(it.template(context -> STR."\{context.arg(Vet.class)}.id = \{context.arg(2)}")))
                .toList();
        assertEquals(2, list.size());
    }
}