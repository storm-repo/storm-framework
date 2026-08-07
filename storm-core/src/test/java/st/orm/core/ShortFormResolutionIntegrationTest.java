package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.EQUALS;
import static st.orm.core.template.TemplateString.raw;

import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.PetOwnerRef_;
import st.orm.core.model.VisitWithTwoPetRefs;
import st.orm.core.model.VisitWithTwoPetRefs_;
import st.orm.core.model.VisitWithTwoPets;
import st.orm.core.model.VisitWithTwoPets_;
import st.orm.core.template.ORMTemplate;

/**
 * Short form names a table by its own metamodel root and resolves against the aliases registered in the query; a
 * nested path names its route and can never be ambiguous. These tests pin the resolution contract for entities that
 * appear more than once: short form fails loudly, naming the candidate paths, while pinned paths resolve — including
 * paths beyond a {@link st.orm.Ref}, whose joins materialize per path.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class ShortFormResolutionIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void shortFormAgainstTwoEagerPathsNamesTheCandidates() {
        var orm = ORMTemplate.of(dataSource);
        var e = assertThrows(PersistenceException.class, () -> orm.entity(VisitWithTwoPets.class).select()
                .where(raw("\0 = \0", PetOwnerRef_.name, "Leo"))
                .getResultList());
        var message = e.getCause().getMessage();
        assertTrue(message.contains("Multiple paths found for PetOwnerRef"), message);
        assertTrue(message.contains("'pet1'") && message.contains("'pet2'"), message);
    }

    @Test
    public void nestedPathPinsThroughTwoEagerPaths() {
        var orm = ORMTemplate.of(dataSource);
        var visits = orm.entity(VisitWithTwoPets.class).select()
                .where(VisitWithTwoPets_.pet1.name, EQUALS, "Leo")
                .getResultList();
        assertFalse(visits.isEmpty());
    }

    @Test
    public void beyondRefPathPinsWithTwoRefsOfTheSameType() {
        var orm = ORMTemplate.of(dataSource);
        // Both fields are Ref<PetOwnerRef>: the on-demand join materializes per path, so the path is never ambiguous.
        var visits = orm.entity(VisitWithTwoPetRefs.class).select()
                .where(VisitWithTwoPetRefs_.pet1.name, EQUALS, "Leo")
                .getResultList();
        assertFalse(visits.isEmpty());
    }

    @Test
    public void bothBeyondRefPathsResolveInOneQuery() {
        var orm = ORMTemplate.of(dataSource);
        var visits = orm.entity(VisitWithTwoPetRefs.class).select()
                .where(VisitWithTwoPetRefs_.pet1.name, EQUALS, "Leo")
                .where(VisitWithTwoPetRefs_.pet2.name, EQUALS, "Leo")
                .getResultList();
        assertFalse(visits.isEmpty());
    }

    @Test
    public void shortFormAgainstUntraversedRefTargetsStatesTheCause() {
        var orm = ORMTemplate.of(dataSource);
        // A Ref registers no alias until a path traverses it, so the table is not part of the query.
        var e = assertThrows(PersistenceException.class, () -> orm.entity(VisitWithTwoPetRefs.class).select()
                .where(raw("\0 = \0", PetOwnerRef_.name, "Leo"))
                .getResultList());
        var message = e.getCause().getMessage();
        assertTrue(message.contains("PetOwnerRef is not part of this query"), message);
    }

    @Test
    public void shortFormResolvesAgainstASingleTraversedRefPath() {
        var orm = ORMTemplate.of(dataSource);
        // One traversal registers one alias, so the short form binds to it. Context-dependent by design: short form
        // resolves iff exactly one alias for the table is registered in the query.
        var visits = orm.entity(VisitWithTwoPetRefs.class).select()
                .where(VisitWithTwoPetRefs_.pet1.name, EQUALS, "Leo")
                .where(raw("\0 = \0", PetOwnerRef_.birthDate, LocalDate.of(2020, 9, 7)))
                .getResultList();
        assertFalse(visits.isEmpty());
    }

    @Test
    public void shortFormAfterBothRefPathsTraversedNamesTheCandidates() {
        var orm = ORMTemplate.of(dataSource);
        var e = assertThrows(PersistenceException.class, () -> orm.entity(VisitWithTwoPetRefs.class).select()
                .where(VisitWithTwoPetRefs_.pet1.name, EQUALS, "Leo")
                .where(VisitWithTwoPetRefs_.pet2.name, EQUALS, "Leo")
                .where(raw("\0 = \0", PetOwnerRef_.birthDate, LocalDate.of(2020, 9, 7)))
                .getResultList());
        var message = e.getCause().getMessage();
        assertTrue(message.contains("Multiple paths found for PetOwnerRef"), message);
        assertTrue(message.contains("'pet1'") && message.contains("'pet2'"), message);
    }
}
