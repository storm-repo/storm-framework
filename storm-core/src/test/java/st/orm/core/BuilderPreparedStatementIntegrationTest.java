package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.IN;
import static st.orm.Operator.NOT_EQUALS;
import static st.orm.Operator.NOT_IN;
import static st.orm.core.template.TemplateString.raw;

import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.Pet;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class BuilderPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testBuilderWithJoin() {
        // 3 visits on 2023-01-08: visit ids 7, 8, 9 for pets 4, 1, 2 respectively.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(raw("\0.id = \0.pet_id", Pet.class, Visit.class))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinTemplateFunction() {
        // Same join as testBuilderWithJoin but using TemplateBuilder.create for the ON clause.
        // 3 visits on 2023-01-08.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Pet.class)
                .innerJoin(Visit.class).on(TemplateBuilder.create(it -> "%s.id = %s.pet_id".formatted(it.insert(Pet.class), it.insert(Visit.class))))
                .where(raw("\0.visit_date = \0", Visit.class, LocalDate.of(2023, 1, 8)))
                .getResultList();
         assertEquals(3, list.size());
    }

    @Test
    public void testBuilderWithJoinParameter() {
        // ON clause matches pet id=1 (Leo) for all visits. WHERE filters to 3 visits on 2023-01-08.
        // Since the join condition is "p.id = 1", all 3 visits produce the same pet (Leo).
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
        // Visit id=1 references pet_id=7 (Samantha). Auto-join infers FK between Visit and Pet.
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
        // Vet has no FK relationship to Visit; whereAny(Vet) should throw SqlTemplateException.
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
        // 8 distinct pets have visits (pets 1-8). Total visits across all pets = 14.
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
        // data.sql inserts 5 vet_specialty rows. Inner join returns one row per vet-specialty pair.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithDoubleCompoundPkJoin() {
        // Same 5 vet_specialty rows, now also joined to Specialty. Still 5 rows.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .getResultList();
        assertEquals(5, list.size());
    }

    @Test
    public void testBuilderWithMultipleWhere() {
        // Chaining multiple .where() calls is not supported; should throw SqlTemplateException.
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
        // Vet ids 1 and 2 both exist in data.sql. OR predicate should match exactly 2 vets.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(it -> it.whereId(1).or(it.whereId(2)))
                .getResultList();
        assertEquals(2, list.size());
    }

    @Test
    public void testBuilderWithWhereEmpty() {
        // Filtering by an empty entity list should return 0 results (no match criteria).
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyEquals() {
        // EQUALS with an empty list is not supported; should throw SqlTemplateException.
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
        // IN with an empty list is valid and matches nothing; 0 results expected.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.id, IN, List.of())
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithWhereEmptyNotEquals() {
        // NOT_EQUALS with an empty list is not supported; should throw SqlTemplateException.
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
        // NOT IN with an empty list matches all rows. data.sql has 6 vets.
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
        // "1 = 1" matches all rows. data.sql has 6 vets.
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
