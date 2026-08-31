package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Owner_;
import st.orm.core.model.Pet;
import st.orm.core.model.Pet_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.template.ORMTemplate;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@JdbcTest
public class NarrowWidenIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void joinedQueryReferencesTheJoinedEntityWithoutAnyVariants() {
        var orm = ORMTemplate.of(dataSource);
        // A joined column named through plain where(), no whereAny needed.
        var pets = orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .where(Visit_.pet.id, st.orm.Operator.EQUALS, 7)
                .getResultList();
        assertFalse(pets.isEmpty());
    }

    @Test
    public void widenAcceptsAGraphPredicateWithoutAJoin() {
        var orm = ORMTemplate.of(dataSource);
        // Owner is part of Pet's eager graph: widen() admits the short-form predicate; resolution pins it at build
        // time. Equivalent to the nested path Pet_.owner.firstName, spelled from the entity's own root.
        var pets = orm.entity(Pet.class).select()
                .widen()
                .where(Owner_.firstName, EQUALS, "Betty")
                .getResultList();
        assertFalse(pets.isEmpty());
        for (Pet pet : pets) {
            assertEquals("Betty", pet.owner().firstName());
        }
    }

    @Test
    public void widenKeepsTheResolutionGuards() {
        var orm = ORMTemplate.of(dataSource);
        // Widening admits the reference; it does not weaken resolution. A table outside the query still fails.
        var e = assertThrows(PersistenceException.class, () -> orm.entity(City.class).select()
                .widen()
                .where(Visit_.visitDate, EQUALS, java.time.LocalDate.now())
                .getResultList());
        assertTrue(e.getMessage().contains("Visit is not part of this query rooted at City"), e.getMessage());
    }

    @Test
    public void widenAndNarrowRoundTrip() {
        var orm = ORMTemplate.of(dataSource);
        var grouped = orm.entity(Pet.class).select()
                .widen()
                .where(Owner_.firstName, EQUALS, "Betty")
                .narrow(Pet.class)
                .getResultGroupedBy(Pet_.owner);
        assertFalse(grouped.isEmpty());
    }

    @Test
    public void narrowRejectsATypeThisQueryIsNotRootedAt() {
        var orm = ORMTemplate.of(dataSource);
        var exception = assertThrows(PersistenceException.class, () -> orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .narrow(City.class));
        assertTrue(exception.getMessage().contains("Root type mismatch"), exception.getMessage());
    }

    @Test
    public void narrowRecoversGroupingAfterAJoin() {
        var orm = ORMTemplate.of(dataSource);
        var grouped = orm.entity(Pet.class).select()
                .innerJoin(Visit.class).on(Pet.class)
                .narrow(Pet.class)
                .getResultGroupedBy(Pet_.owner);
        assertFalse(grouped.isEmpty());
        for (Owner owner : grouped.keySet()) {
            assertFalse(grouped.get(owner).isEmpty());
        }
    }
}
