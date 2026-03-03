package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.Templates.param;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.core.IntegrationConfig;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Query;

/**
 * Integration tests targeting uncovered branches in QueryImpl, including:
 * - readColumnValue type mapping for Calendar, ByteBuffer, java.util.Date
 * - getResultStream(Class) error paths
 * - getRefStream
 * - executeBatch
 * - toString
 * - managed() and unsafe()
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class QueryImplTest {

    @Autowired
    private DataSource dataSource;

    // readColumnValue: Calendar type mapping

    record CalendarResult(Calendar value) {}

    @Test
    public void testCalendarTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        CalendarResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(CalendarResult.class);
        assertNotNull(result.value());
        assertEquals(2023, result.value().get(Calendar.YEAR));
        assertEquals(Calendar.JUNE, result.value().get(Calendar.MONTH));
    }

    @Test
    public void testNullCalendarMapping() {
        var orm = ORMTemplate.of(dataSource);
        CalendarResult result = orm.query(raw("SELECT CAST(NULL AS TIMESTAMP)"))
                .getSingleResult(CalendarResult.class);
        // Calendar is not primitive, so null is valid
        assertTrue(result.value() == null);
    }

    // readColumnValue: java.sql.Timestamp type mapping

    record TimestampResult(Timestamp value) {}

    @Test
    public void testTimestampTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        TimestampResult result = orm.query(raw("SELECT CAST('2023-06-15 10:30:00' AS TIMESTAMP)"))
                .getSingleResult(TimestampResult.class);
        assertNotNull(result.value());
    }

    // readColumnValue: ByteBuffer type mapping

    record ByteBufferResult(ByteBuffer value) {}

    @Test
    public void testByteBufferReadTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ByteBufferResult result = orm.query(raw("SELECT X'DEADBEEF'"))
                .getSingleResult(ByteBufferResult.class);
        assertNotNull(result.value());
        assertTrue(result.value().isReadOnly());
    }

    @Test
    public void testNullByteBufferReadTypeMapping() {
        var orm = ORMTemplate.of(dataSource);
        ByteBufferResult result = orm.query(raw("SELECT CAST(NULL AS BINARY)"))
                .getSingleResult(ByteBufferResult.class);
        // ByteBuffer null when bytes are null
        assertTrue(result.value() == null);
    }

    // getRefStream

    @Test
    public void testGetRefStream() {
        var orm = ORMTemplate.of(dataSource);
        try (Stream<Ref<City>> refs = orm.query(raw("SELECT id FROM city ORDER BY id"))
                .getRefStream(City.class, Integer.class)) {
            List<Ref<City>> refList = refs.toList();
            assertEquals(6, refList.size());
            // Refs should be non-null for non-null PKs
            for (Ref<City> ref : refList) {
                assertNotNull(ref);
            }
        }
    }

    @Test
    public void testGetRefStreamWithNullPk() {
        var orm = ORMTemplate.of(dataSource);
        // Pets with NULL owner_id: pet 13 ("Sly") has NULL owner
        // Null PK causes construction failure, so expect PersistenceException
        assertThrows(PersistenceException.class, () -> {
            try (Stream<Ref<Owner>> refs = orm.query(raw("SELECT owner_id FROM pet WHERE owner_id IS NULL"))
                    .getRefStream(Owner.class, Integer.class)) {
                refs.toList();
            }
        });
    }

    // executeBatch path

    @Test
    public void testExecuteBatch() {
        var orm = ORMTemplate.of(dataSource);
        try (var query = orm.query(raw("INSERT INTO city (name) VALUES (\0)", param("TestCity"))).prepare()) {
            int[] result = query.executeBatch();
            assertNotNull(result);
        }
    }

    // getResultStream(Class) with type mismatch

    record WrongColumnCount(int first, int second, int third) {}

    @Test
    public void testGetResultStreamTypeMismatchThrows() {
        var orm = ORMTemplate.of(dataSource);
        // SELECT only 1 column but record expects 3 - should throw due to no suitable constructor
        assertThrows(PersistenceException.class, () ->
                orm.query(raw("SELECT id FROM city WHERE id = 1"))
                        .getSingleResult(WrongColumnCount.class));
    }

    // Query.managed() and Query.unsafe()

    @Test
    public void testManagedQuery() {
        var orm = ORMTemplate.of(dataSource);
        Query query = orm.selectFrom(City.class).build();
        Query managedQuery = query.managed();
        assertNotNull(managedQuery);
        // Managed query should still be able to get results
        long count = managedQuery.getResultCount();
        assertEquals(6, count);
    }

    @Test
    public void testUnsafeQuery() {
        var orm = ORMTemplate.of(dataSource);
        // Build an UPDATE without WHERE - normally rejected
        Query query = orm.query(raw("UPDATE city SET name = 'x'"));
        // Without unsafe() this should throw
        assertThrows(PersistenceException.class, () -> query.executeUpdate());
        // With unsafe() it should succeed
        Query unsafeQuery = query.unsafe();
        int updated = unsafeQuery.executeUpdate();
        assertTrue(updated > 0);
    }

    // Query.isVersionAware()

    @Test
    public void testIsVersionAware() {
        var orm = ORMTemplate.of(dataSource);
        Query query = orm.selectFrom(City.class).build();
        assertFalse(query.isVersionAware());
    }

    // getResultStream() returning Object[] - multiple columns

    @Test
    public void testGetResultStreamObjectArrayMultipleColumns() {
        var orm = ORMTemplate.of(dataSource);
        try (Stream<Object[]> stream = orm.query(raw("SELECT id, name FROM city WHERE id = 1")).getResultStream()) {
            Object[] row = stream.findFirst().orElseThrow();
            assertEquals(2, row.length);
        }
    }

    // getResultList(Class)

    @Test
    public void testGetResultListTyped() {
        var orm = ORMTemplate.of(dataSource);
        record CityName(String name) {}
        List<CityName> results = orm.query(raw("SELECT name FROM city ORDER BY id"))
                .getResultList(CityName.class);
        assertEquals(6, results.size());
        assertEquals("Sun Paririe", results.getFirst().name());
    }

    // getOptionalResult returns empty for no rows

    @Test
    public void testGetOptionalResultReturnsEmpty() {
        var orm = ORMTemplate.of(dataSource);
        record CityName(String name) {}
        var result = orm.query(raw("SELECT name FROM city WHERE id = 999"))
                .getOptionalResult(CityName.class);
        assertTrue(result.isEmpty());
    }

    // PreparedQuery: getResultStream(Class) on prepared query

    @Test
    public void testPreparedQueryGetResultStreamTyped() {
        var orm = ORMTemplate.of(dataSource);
        record CityName(String name) {}
        try (var query = orm.query(raw("SELECT name FROM city WHERE id = \0", param(1))).prepare();
             var stream = query.getResultStream(CityName.class)) {
            CityName result = stream.findFirst().orElseThrow();
            assertEquals("Sun Paririe", result.name());
        }
    }
}
