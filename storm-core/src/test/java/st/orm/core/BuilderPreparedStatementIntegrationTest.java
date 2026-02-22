package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;
import static st.orm.Operator.GREATER_THAN;
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
import st.orm.Slice;
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
        // Multiple .where() calls should be AND-combined. ID 1 AND ID 2 can't both match,
        // so the result should be empty.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .typed(Integer.class)
                .where(1)
                .where(2)
                .getResultList();
        assertEquals(0, list.size());
    }

    @Test
    public void testBuilderWithMultipleWhereMatching() {
        // Using two .where() calls that don't conflict: filter by first name AND last name.
        // Vet with ID=1 is "James Carter".
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.firstName, EQUALS, "James")
                .where(Vet_.lastName, EQUALS, "Carter")
                .getResultList();
        assertEquals(1, list.size());
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

    // Composable ORDER BY tests.

    @Test
    public void testBuilderWithMultipleOrderBy() {
        // Multiple orderBy calls should produce a single ORDER BY with comma-separated columns.
        var list = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .orderBy(Vet_.lastName)
                .orderBy(Vet_.firstName)
                .getResultList();
        assertEquals(6, list.size());
        // Verify the list is sorted by lastName, then firstName.
        for (int i = 1; i < list.size(); i++) {
            int cmp = list.get(i - 1).lastName().compareTo(list.get(i).lastName());
            if (cmp == 0) {
                assertTrue(list.get(i - 1).firstName().compareTo(list.get(i).firstName()) <= 0);
            } else {
                assertTrue(cmp < 0);
            }
        }
    }

    // Slice pagination tests.

    @Test
    public void testSliceBasic() {
        // There are 6 vets. A slice of 3 should have hasNext=true.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .orderBy(Vet_.id)
                .slice(3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
    }

    @Test
    public void testSliceLastPage() {
        // There are 6 vets. A slice of 10 should have hasNext=false.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .orderBy(Vet_.id)
                .slice(10);
        assertEquals(6, slice.content().size());
        assertFalse(slice.hasNext());
    }

    @Test
    public void testSliceWithKey() {
        // First page of 3 vets ordered by id.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.id, 3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
    }

    @Test
    public void testSliceAfter() {
        // Get vets after id=3, ascending. Should get vets 4, 5, 6.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.id, 3, 10);
        assertEquals(3, slice.content().size());
        assertFalse(slice.hasNext());
        assertTrue(slice.content().stream().allMatch(v -> v.id() > 3));
    }

    @Test
    public void testSliceBefore() {
        // Get vets before id=4, descending. Should get vets 3, 2, 1.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, 4, 10);
        assertEquals(3, slice.content().size());
        assertFalse(slice.hasNext());
        assertTrue(slice.content().stream().allMatch(v -> v.id() < 4));
    }

    @Test
    public void testSliceAfterWithExistingWhere() {
        // Composable: WHERE firstName = 'James' AND id > 0.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.firstName, EQUALS, "James")
                .sliceAfter(Vet_.id, 0, 10);
        // There's one vet named James (James Carter, id=1).
        assertEquals(1, slice.content().size());
        assertFalse(slice.hasNext());
    }

    @Test
    public void testSliceAfterThrowsWithExplicitOrderBy() {
        // sliceAfter should throw if orderBy was already called.
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.lastName)
                    .sliceAfter(Vet_.id, 0, 10);
        });
    }

    @Test
    public void testSliceBeforeThrowsWithExplicitOrderBy() {
        // sliceBefore should throw if orderBy was already called.
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.lastName)
                    .sliceBefore(Vet_.id, 10, 10);
        });
    }

    // Cursorless descending keyset pagination tests.

    @Test
    public void testSliceBeforeCursorless() {
        // First page of 3 vets ordered by id DESC (cursorless sliceBefore).
        // There are 6 vets, so hasNext=true. Expected ids: 6, 5, 4.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, 3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
        assertEquals(6, slice.content().get(0).id());
        assertEquals(5, slice.content().get(1).id());
        assertEquals(4, slice.content().get(2).id());
    }

    @Test
    public void testSliceBeforeCursorlessAllResults() {
        // Request more than available: all 6 vets in descending order.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, 10);
        assertEquals(6, slice.content().size());
        assertFalse(slice.hasNext());
        assertEquals(6, slice.content().get(0).id());
        assertEquals(1, slice.content().get(5).id());
    }

    @Test
    public void testSliceBeforeCursorlessThrowsWithExplicitOrderBy() {
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.lastName)
                    .sliceBefore(Vet_.id, 10);
        });
    }

    @Test
    public void testSliceBeforeCursorlessComposite() {
        // First page of 3 vets ordered by lastName DESC, id DESC.
        // Expected order: Stevens(5), Ortega(4), Leary(2), Jenkins(6), Douglas(3), Carter(1).
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.lastName, Vet_.id, 3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
        assertEquals("Stevens", slice.content().get(0).lastName());
        assertEquals("Ortega", slice.content().get(1).lastName());
        assertEquals("Leary", slice.content().get(2).lastName());
    }

    @Test
    public void testSliceBeforeCursorlessCompositeAllResults() {
        // Request more than available: all 6 vets in descending composite order.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.lastName, Vet_.id, 10);
        assertEquals(6, slice.content().size());
        assertFalse(slice.hasNext());
        assertEquals("Stevens", slice.content().get(0).lastName());
        assertEquals("Carter", slice.content().get(5).lastName());
    }

    @Test
    public void testSliceBeforeCursorlessCompositeThrowsWithExplicitOrderBy() {
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.lastName)
                    .sliceBefore(Vet_.lastName, Vet_.id, 10);
        });
    }

    // Composite keyset pagination tests.

    @Test
    public void testCompositeSliceFirstPage() {
        // First page of 3 vets ordered by lastName ASC, id ASC.
        // Expected order: Carter(1), Douglas(3), Jenkins(6), Leary(2), Ortega(4), Stevens(5).
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.lastName, Vet_.id, 3);
        assertEquals(3, slice.content().size());
        assertTrue(slice.hasNext());
        assertEquals("Carter", slice.content().get(0).lastName());
        assertEquals("Douglas", slice.content().get(1).lastName());
        assertEquals("Jenkins", slice.content().get(2).lastName());
    }

    @Test
    public void testCompositeSliceAfter() {
        // Get vets after (lastName="Douglas", id=3), ascending.
        // Should get: Jenkins(6), Leary(2), Ortega(4), Stevens(5).
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.lastName, "Douglas", Vet_.id, 3, 10);
        assertEquals(4, slice.content().size());
        assertFalse(slice.hasNext());
        assertEquals("Jenkins", slice.content().get(0).lastName());
        assertEquals("Leary", slice.content().get(1).lastName());
        assertEquals("Ortega", slice.content().get(2).lastName());
        assertEquals("Stevens", slice.content().get(3).lastName());
    }

    @Test
    public void testCompositeSliceBefore() {
        // Get vets before (lastName="Leary", id=2), descending.
        // Should get: Jenkins(6), Douglas(3), Carter(1).
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.lastName, "Leary", Vet_.id, 2, 10);
        assertEquals(3, slice.content().size());
        assertFalse(slice.hasNext());
        // Results are in descending order.
        assertEquals("Jenkins", slice.content().get(0).lastName());
        assertEquals("Douglas", slice.content().get(1).lastName());
        assertEquals("Carter", slice.content().get(2).lastName());
    }

    @Test
    public void testCompositeSliceAfterWithExistingWhere() {
        // Composable: WHERE firstName starts with a consonant AND composite cursor.
        // Filter vets to those with lastName > 'D' using a where clause, then slice after (lastName="Jenkins", id=6).
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .where(Vet_.lastName, GREATER_THAN, "D")
                .sliceAfter(Vet_.lastName, "Jenkins", Vet_.id, 6, 10);
        // Remaining after "Jenkins": Leary(2), Ortega(4), Stevens(5).
        assertEquals(3, slice.content().size());
        assertFalse(slice.hasNext());
        assertEquals("Leary", slice.content().get(0).lastName());
    }

    @Test
    public void testCompositeSliceAfterThrowsWithExplicitOrderBy() {
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.firstName)
                    .sliceAfter(Vet_.lastName, "Carter", Vet_.id, 1, 10);
        });
    }

    @Test
    public void testCompositeSliceBeforeThrowsWithExplicitOrderBy() {
        assertThrows(PersistenceException.class, () -> {
            ORMTemplate.of(dataSource)
                    .selectFrom(Vet.class)
                    .orderBy(Vet_.firstName)
                    .sliceBefore(Vet_.lastName, "Stevens", Vet_.id, 5, 10);
        });
    }

    @Test
    public void testCompositeSliceAfterPagination() {
        // Page through all vets in pages of 2, ordered by lastName ASC, id ASC.
        // Page 1: Carter(1), Douglas(3).
        Slice<Vet> page1 = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.lastName, Vet_.id, 2);
        assertEquals(2, page1.content().size());
        assertTrue(page1.hasNext());
        assertEquals("Carter", page1.content().get(0).lastName());
        assertEquals("Douglas", page1.content().get(1).lastName());

        // Page 2: Jenkins(6), Leary(2).
        Vet lastPage1 = page1.content().get(page1.content().size() - 1);
        Slice<Vet> page2 = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.lastName, lastPage1.lastName(), Vet_.id, lastPage1.id(), 2);
        assertEquals(2, page2.content().size());
        assertTrue(page2.hasNext());
        assertEquals("Jenkins", page2.content().get(0).lastName());
        assertEquals("Leary", page2.content().get(1).lastName());

        // Page 3: Ortega(4), Stevens(5).
        Vet lastPage2 = page2.content().get(page2.content().size() - 1);
        Slice<Vet> page3 = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.lastName, lastPage2.lastName(), Vet_.id, lastPage2.id(), 2);
        assertEquals(2, page3.content().size());
        assertFalse(page3.hasNext());
        assertEquals("Ortega", page3.content().get(0).lastName());
        assertEquals("Stevens", page3.content().get(1).lastName());
    }
}
