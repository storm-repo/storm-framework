package st.orm.template;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.template.SqlInterceptor;
import st.orm.template.model.Owner;
import st.orm.template.model.Pet;
import st.orm.template.model.PetType;
import st.orm.template.model.Visit;

/**
 * Reproduces the join-ordering issue observed with a custom join whose ON clause references an
 * auto-joined table that is derived from a nullable FK (and therefore emitted as LEFT JOIN).
 *
 * <p>Model shape: Visit --(non-null FK)--> Pet --(non-null FK)--> PetType and
 * Pet --(nullable FK)--> Owner. The custom join targets Owner, so its ON clause references the
 * LEFT-joined owner alias. PostgreSQL requires the referenced alias to be declared before use;
 * H2 tolerates forward references, which masks the problem in H2-based tests.</p>
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@SpringBootTest
@Sql("/data.sql")
public class JoinOrderTest {

    @Autowired
    private ORMTemplate orm;

    public record TypeCount(PetType type, long count) {}

    @Test
    public void customJoinReferencingNullableAutoJoinedTable_selectFrom() {
        String sql = capture(() ->
                orm.selectFrom(Visit.class, TypeCount.class, RAW."\{PetType.class}, COUNT(*)")
                        .innerJoin(Pet.class).on(Owner.class)
                        .groupBy(RAW."\{PetType.class}.id")
                        .getResultList());
        System.out.println("[JoinOrderTest selectFrom] Generated SQL:\n" + sql);
        assertValid(sql);
    }

    @Test
    public void customJoinReferencingNullableAutoJoinedTable_repositorySelect() {
        String sql = capture(() ->
                orm.entity(Visit.class)
                        .select(TypeCount.class, RAW."\{PetType.class}, COUNT(*)")
                        .innerJoin(Pet.class).on(Owner.class)
                        .groupBy(RAW."\{PetType.class}.id")
                        .getResultList());
        System.out.println("[JoinOrderTest repository] Generated SQL:\n" + sql);
        assertValid(sql);
    }

    private String capture(Runnable query) {
        List<String> statements = new ArrayList<>();
        SqlInterceptor.observe(sql -> statements.add(sql.statement()), query::run);
        return statements.getFirst();
    }

    private void assertValid(String sql) {
        // Every alias referenced in an ON clause must be declared by a preceding FROM/JOIN.
        assertTrue(aliasesDeclaredBeforeUse(sql), "ON clause references an alias before it is declared:\n" + sql);
        // Outer joins come as late as dependencies allow: an inner join may only appear after an outer join
        // when its ON clause references a table declared at or after the first outer join.
        assertTrue(outerJoinsAsLateAsPossible(sql), "Independent INNER JOIN found after an outer join:\n" + sql);
    }

    private static boolean outerJoinsAsLateAsPossible(String sql) {
        var deferredAliases = new java.util.HashSet<String>();
        boolean outerSeen = false;
        for (String line : sql.split("\n")) {
            var joinMatcher = java.util.regex.Pattern
                    .compile("(LEFT|RIGHT|INNER|CROSS)?\\s*JOIN\\s+\\w+\\s+(\\w+)(?:\\s+ON\\s+(.*))?", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(line.trim());
            if (!joinMatcher.find()) {
                continue;
            }
            boolean outer = joinMatcher.group(1) != null
                    && (joinMatcher.group(1).equalsIgnoreCase("LEFT") || joinMatcher.group(1).equalsIgnoreCase("RIGHT"));
            String alias = joinMatcher.group(2);
            String onClause = joinMatcher.group(3) == null ? "" : joinMatcher.group(3);
            if (outerSeen) {
                var refMatcher = java.util.regex.Pattern.compile("(\\w+)\\.").matcher(onClause);
                boolean referencesDeferred = outer;
                while (refMatcher.find()) {
                    if (deferredAliases.contains(refMatcher.group(1))) {
                        referencesDeferred = true;
                    }
                }
                if (!referencesDeferred) {
                    return false;
                }
                deferredAliases.add(alias);
            } else if (outer) {
                outerSeen = true;
                deferredAliases.add(alias);
            }
        }
        return true;
    }

    private static boolean aliasesDeclaredBeforeUse(String sql) {
        // Collect declared aliases in order and check each ON clause only uses aliases declared so far.
        var declared = new java.util.HashSet<String>();
        for (String line : sql.split("\n")) {
            var joinMatcher = java.util.regex.Pattern
                    .compile("(?:FROM|JOIN)\\s+\\w+\\s+(\\w+)(?:\\s+ON\\s+(.*))?", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(line.trim());
            if (joinMatcher.find()) {
                String alias = joinMatcher.group(1);
                String onClause = joinMatcher.group(2);
                if (onClause != null) {
                    var refMatcher = java.util.regex.Pattern.compile("(\\w+)\\.").matcher(onClause);
                    var refs = new java.util.HashSet<String>();
                    while (refMatcher.find()) {
                        refs.add(refMatcher.group(1));
                    }
                    refs.remove(alias);
                    if (!declared.containsAll(refs)) {
                        return false;
                    }
                }
                declared.add(alias);
            }
        }
        return true;
    }
}
