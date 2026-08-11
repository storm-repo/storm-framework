package st.orm.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import st.orm.Entity;
import st.orm.PK;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies that {@link StormTest} on an abstract base class applies to concrete subclasses: the extension finds the
 * inherited annotation, executes its scripts, and uses the {@code dataSource()} factory declared on the base.
 */
class StormExtensionInheritedTest extends StormExtensionInheritedBase {

    record Item(@PK Integer id, String name) implements Entity<Integer> {}

    @Test
    void inheritedFactoryShouldProvideDataSource(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection()) {
            var url = conn.getMetaData().getURL();
            assertTrue(url.contains(INHERITED_DB_NAME),
                    "Expected connection URL to contain '" + INHERITED_DB_NAME + "' but got: " + url);
        }
    }

    @Test
    void scriptsShouldExecuteForInheritedAnnotation(ORMTemplate orm) {
        var items = orm.entity(Item.class).findAll();
        assertEquals(3, items.size());
    }
}
