package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Operator.EQUALS;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.City;
import st.orm.core.model.City_;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.TemplateString;

/**
 * Integration tests for DeleteBuilder covering distinct, offset, limit, forShare, forUpdate,
 * forLock, subquery, and getResultStream restrictions.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class DeleteBuilderIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDeleteDistinctThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).distinct());
    }

    @Test
    public void testDeleteOffsetThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).offset(1));
    }

    @Test
    public void testDeleteLimitThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).limit(1));
    }

    @Test
    public void testDeleteForShareThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).forShare());
    }

    @Test
    public void testDeleteForUpdateThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).forUpdate());
    }

    @Test
    public void testDeleteForLockThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).forLock(TemplateString.of("FOR LOCK")));
    }

    @Test
    public void testDeleteGetResultStreamThrows() {
        var orm = ORMTemplate.of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).unsafe().getResultStream());
    }

    @Test
    public void testDeleteWithWhereClause() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        // Insert a city we can delete.
        var insertedId = cities.insertAndFetchId(City.builder().name("DeleteMe").build());
        long countBefore = cities.count();

        orm.deleteFrom(City.class)
                .where(City_.id, EQUALS, insertedId)
                .executeUpdate();

        assertEquals(countBefore - 1, cities.count());
    }

    @Test
    public void testDeleteWithUnsafe() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        // Insert a standalone city.
        cities.insertAndFetchId(City.builder().name("UnsafeDelete").build());

        // Unsafe delete without WHERE should attempt to delete all.
        // This will fail due to FK constraints on existing cities, which is expected.
        assertThrows(PersistenceException.class,
                () -> orm.deleteFrom(City.class).unsafe().executeUpdate());
    }
}
