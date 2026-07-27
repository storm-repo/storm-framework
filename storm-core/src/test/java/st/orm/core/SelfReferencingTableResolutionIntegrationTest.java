package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Operator.IN;
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
import st.orm.core.model.Country;
import st.orm.core.model.User;
import st.orm.core.model.UserScore;
import st.orm.core.model.UserScore_;
import st.orm.core.template.ORMTemplate;

/**
 * A table that is reachable from itself occurs more than once in the table graph: once where the query navigated to it,
 * and once more for every trip around the cycle. Country is reachable from itself through its capital and its largest
 * city, both of which refer back to the country they lie in, so a query rooted at a user's score sees country at
 * {@code user.country}, at {@code user.country.capital.country} and at {@code user.country.largestCity.country}.
 *
 * <p>Selecting the country by type has to pick one of those. The occurrence the query navigated to is the only one the
 * caller can have meant, since the others are only reachable by passing the country again.</p>
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class SelfReferencingTableResolutionIntegrationTest {

    /** Aggregate over user scores, per country and date. */
    public record CountryScoreRow(Country country, LocalDate scoreDate, long scoreCount, double averageScore) {}

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelectSelfReferencingTableByTypeInAggregate() {
        var orm = ORMTemplate.of(dataSource);
        // Country is named by type in the select clause while the grouping names it by path. Both have to land on the
        // occurrence at user.country rather than on one of the two reached by going around the cycle.
        List<CountryScoreRow> rows = orm.entity(UserScore.class)
                .select(CountryScoreRow.class,
                        raw("\0, \0, COUNT(*), AVG(\0)", Country.class, UserScore_.scoreDate, UserScore_.score))
                .where(UserScore_.scoreDate, IN, List.of(LocalDate.of(2026, 7, 25), LocalDate.of(2026, 7, 26)))
                .groupBy(UserScore_.user.country, UserScore_.scoreDate)
                .getResultList();
        // Three (country, date) groups: the Netherlands on both dates, Belgium on the first.
        assertEquals(3, rows.size());
        rows.forEach(row -> assertNotNull(row.country()));
        var netherlandsOnTheFirstDate = rows.stream()
                .filter(row -> "Netherlands".equals(row.country().name()))
                .filter(row -> LocalDate.of(2026, 7, 25).equals(row.scoreDate()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, netherlandsOnTheFirstDate.scoreCount());
        assertEquals(0.30, netherlandsOnTheFirstDate.averageScore(), 1e-9);
    }

    @Test
    public void testSelectedCountryIsTheOneTheUserLivesIn() {
        var orm = ORMTemplate.of(dataSource);
        // The country selected by type is the one the user lives in, not a country reached back through a city.
        List<CountryScoreRow> rows = orm.entity(UserScore.class)
                .select(CountryScoreRow.class,
                        raw("\0, \0, COUNT(*), AVG(\0)", Country.class, UserScore_.scoreDate, UserScore_.score))
                .groupBy(UserScore_.user.country, UserScore_.scoreDate)
                .getResultList();
        var userCountryIds = orm.entity(User.class).select().getResultList().stream()
                .map(user -> user.country().id())
                .distinct()
                .sorted()
                .toList();
        var selectedCountryIds = rows.stream().map(row -> row.country().id()).distinct().sorted().toList();
        assertFalse(selectedCountryIds.isEmpty());
        assertTrue(userCountryIds.containsAll(selectedCountryIds));
    }

    @Test
    public void testSelectSelfReferencingTableAsRoot() {
        var orm = ORMTemplate.of(dataSource);
        // Reading the country itself still works: the root occurrence is the one reached without passing it twice.
        var countries = orm.entity(Country.class).select().getResultList();
        assertEquals(2, countries.size());
        countries.forEach(country -> {
            assertNotNull(country.capital());
            assertNotNull(country.largestCity());
        });
    }

    @Test
    public void testNavigatingBeyondTheCycleStillResolvesByPath() {
        var orm = ORMTemplate.of(dataSource);
        // Naming the far side of the cycle by path is unambiguous and keeps working.
        var scores = orm.entity(UserScore.class).select()
                .where(UserScore_.user.country.capital.name, st.orm.Operator.EQUALS, "Amsterdam")
                .getResultList();
        assertEquals(3, scores.size());
    }
}
