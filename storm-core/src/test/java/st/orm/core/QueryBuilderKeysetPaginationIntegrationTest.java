package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.Slice;
import st.orm.core.model.Vet;
import st.orm.core.model.Vet_;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests for {@link st.orm.core.template.QueryBuilder} keyset pagination methods,
 * covering edge cases not tested elsewhere: slice(Key, size) with explicit orderBy,
 * slice(Key, Metamodel, size) with explicit orderBy, sliceBefore(Key, Metamodel, size)
 * with explicit orderBy, and orderByDescendingAny with empty array.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QueryBuilderKeysetPaginationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // slice(Key, size) with explicit orderBy should throw

    @Test
    public void sliceWithKeyThrowsWhenExplicitOrderByIsPresent() {
        assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderBy(Vet_.lastName)
                        .slice(Vet_.id, 3));
    }

    // sliceBefore(Key, size) - cursorless descending

    @Test
    public void sliceBeforeCursorlessWithKeyThrowsWhenExplicitOrderByIsPresent() {
        assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderBy(Vet_.firstName)
                        .sliceBefore(Vet_.id, 3));
    }

    // slice(Key, Metamodel, size) with explicit orderBy should throw

    @Test
    public void sliceCompositeWithKeyAndSortThrowsWhenExplicitOrderByIsPresent() {
        assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderBy(Vet_.firstName)
                        .slice(Vet_.id, Vet_.lastName, 3));
    }

    // orderByDescendingAny with empty array should throw

    @Test
    public void orderByDescendingAnyWithEmptyArrayThrows() {
        assertThrows(PersistenceException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderByDescendingAny());
    }

    // Verify basic keyset pagination behavior with edge case sizes

    @Test
    public void sliceWithKeySizeExactlyMatchingDataShouldHaveNoNext() {
        // There are 6 vets. Requesting exactly 6 should return all with hasNext=false.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.id, 6);
        assertEquals(6, slice.content().size());
        assertFalse(slice.hasNext());
    }

    @Test
    public void sliceWithKeySizeOneLessThanDataShouldHaveNext() {
        // There are 6 vets. Requesting 5 should return 5 with hasNext=true.
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.id, 5);
        assertEquals(5, slice.content().size());
        assertTrue(slice.hasNext());
    }

    // Composite keyset: full forward pagination

    @Test
    public void compositeSliceAfterReturnsCorrectNextPageAndHasNext() {
        // First page of 2 vets ordered by lastName, id.
        Slice<Vet> firstPage = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .slice(Vet_.id, Vet_.lastName, 2);
        assertEquals(2, firstPage.content().size());
        assertTrue(firstPage.hasNext());

        // Second page after the last item of first page.
        Vet lastOfFirstPage = firstPage.content().getLast();
        Slice<Vet> secondPage = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.id, lastOfFirstPage.id(), Vet_.lastName, lastOfFirstPage.lastName(), 2);
        assertEquals(2, secondPage.content().size());
        assertTrue(secondPage.hasNext());

        // Third (last) page.
        Vet lastOfSecondPage = secondPage.content().getLast();
        Slice<Vet> thirdPage = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.id, lastOfSecondPage.id(), Vet_.lastName, lastOfSecondPage.lastName(), 2);
        assertEquals(2, thirdPage.content().size());
        assertFalse(thirdPage.hasNext());
    }

    // Composite keyset: full backward pagination

    @Test
    public void compositeSliceBeforeReturnsCorrectPreviousPage() {
        // Start from the end: first page descending.
        Slice<Vet> lastPage = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, Vet_.lastName, 2);
        assertEquals(2, lastPage.content().size());
        assertTrue(lastPage.hasNext());

        // Previous page before the first item of last page.
        Vet firstOfLastPage = lastPage.content().getLast();
        Slice<Vet> previousPage = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, firstOfLastPage.id(), Vet_.lastName, firstOfLastPage.lastName(), 2);
        assertEquals(2, previousPage.content().size());
        assertTrue(previousPage.hasNext());
    }

    // sliceAfter with value cursor: verify WHERE condition is applied correctly

    @Test
    public void sliceAfterWithValueCursorExcludesCursorValue() {
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceAfter(Vet_.id, 3, 10);
        // Only vets with id > 3 should be returned (4, 5, 6).
        assertEquals(3, slice.content().size());
        assertTrue(slice.content().stream().allMatch(vet -> vet.id() > 3));
    }

    // sliceBefore with value cursor: verify WHERE condition is applied correctly

    @Test
    public void sliceBeforeWithValueCursorExcludesCursorValue() {
        Slice<Vet> slice = ORMTemplate.of(dataSource)
                .selectFrom(Vet.class)
                .sliceBefore(Vet_.id, 4, 10);
        // Only vets with id < 4 should be returned (3, 2, 1) in descending order.
        assertEquals(3, slice.content().size());
        assertTrue(slice.content().stream().allMatch(vet -> vet.id() < 4));
        // Verify descending order.
        assertTrue(slice.content().get(0).id() > slice.content().get(1).id());
    }

    // slice with non-positive size should throw

    @Test
    public void sliceWithZeroSizeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderBy(Vet_.id)
                        .slice(0));
    }

    @Test
    public void sliceWithNegativeSizeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                ORMTemplate.of(dataSource)
                        .selectFrom(Vet.class)
                        .orderBy(Vet_.id)
                        .slice(-1));
    }
}
