package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.ORMTemplate.of;
import static st.orm.core.template.TemplateString.raw;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.PersistenceException;
import st.orm.core.model.City;

/**
 * Integration tests targeting edge cases in SqlTemplateImpl, QueryImpl, PreparedStatementTemplateImpl,
 * and ORMTemplateImpl. Focuses on branches not covered by existing tests: null readColumnValue paths
 * for temporal types, getResultStream(Class) with scalar types, findGenericType edge cases,
 * and query error paths.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class SqlTemplateAndQueryIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // QueryImpl.getResultStream(Class) with single column scalar types

    @Test
    public void getResultStreamWithStringTypeShouldReturnStrings() {
        var orm = of(dataSource);
        try (Stream<String> stream = orm.query("SELECT name FROM city").getResultStream(String.class)) {
            List<String> names = stream.collect(Collectors.toList());
            assertFalse(names.isEmpty());
            assertTrue(names.stream().allMatch(name -> name instanceof String));
        }
    }

    @Test
    public void getResultStreamWithIntegerTypeShouldReturnIntegers() {
        var orm = of(dataSource);
        try (Stream<Integer> stream = orm.query("SELECT id FROM city").getResultStream(Integer.class)) {
            List<Integer> ids = stream.collect(Collectors.toList());
            assertFalse(ids.isEmpty());
            assertTrue(ids.stream().allMatch(id -> id instanceof Integer));
        }
    }

    @Test
    public void getResultStreamWithEmptyResultShouldReturnEmptyStream() {
        var orm = of(dataSource);
        try (Stream<Object[]> stream = orm.query("SELECT id, name FROM city WHERE id = -1").getResultStream()) {
            assertEquals(0, stream.count());
        }
    }

    // readColumnValue with null values for temporal types

    record NullableIntegerResult(Integer value) {}

    @Test
    public void readColumnValueWithNullShouldReturnNull() {
        var orm = of(dataSource);
        NullableIntegerResult result = orm.query("SELECT NULL").getSingleResult(NullableIntegerResult.class);
        assertNull(result.value());
    }

    record UtilDateResult(java.util.Date value) {}

    @Test
    public void readColumnValueWithNullUtilDateShouldReturnNull() {
        var orm = of(dataSource);
        UtilDateResult result = orm.query("SELECT CAST(NULL AS TIMESTAMP)").getSingleResult(UtilDateResult.class);
        assertNull(result.value());
    }

    record OffsetDateTimeResult(OffsetDateTime value) {}

    @Test
    public void readColumnValueWithNullOffsetDateTimeShouldReturnNull() {
        var orm = of(dataSource);
        OffsetDateTimeResult result = orm.query("SELECT CAST(NULL AS TIMESTAMP WITH TIME ZONE)")
                .getSingleResult(OffsetDateTimeResult.class);
        assertNull(result.value());
    }

    record ZonedDateTimeResult(ZonedDateTime value) {}

    @Test
    public void readColumnValueWithNullZonedDateTimeShouldReturnNull() {
        var orm = of(dataSource);
        ZonedDateTimeResult result = orm.query("SELECT CAST(NULL AS TIMESTAMP WITH TIME ZONE)")
                .getSingleResult(ZonedDateTimeResult.class);
        assertNull(result.value());
    }

    record InstantResult(Instant value) {}

    @Test
    public void readColumnValueWithNullInstantShouldReturnNull() {
        var orm = of(dataSource);
        InstantResult result = orm.query("SELECT CAST(NULL AS TIMESTAMP WITH TIME ZONE)")
                .getSingleResult(InstantResult.class);
        assertNull(result.value());
    }

    record LocalDateTimeResult(LocalDateTime value) {}

    @Test
    public void readColumnValueWithNullLocalDateTimeShouldReturnNull() {
        var orm = of(dataSource);
        LocalDateTimeResult result = orm.query("SELECT CAST(NULL AS TIMESTAMP)")
                .getSingleResult(LocalDateTimeResult.class);
        assertNull(result.value());
    }

    record NullableLocalDateResult(LocalDate value) {}

    @Test
    public void readColumnValueWithNullLocalDateShouldReturnNull() {
        var orm = of(dataSource);
        NullableLocalDateResult result = orm.query("SELECT CAST(NULL AS DATE)")
                .getSingleResult(NullableLocalDateResult.class);
        assertNull(result.value());
    }

    record NullableLocalTimeResult(LocalTime value) {}

    @Test
    public void readColumnValueWithNullLocalTimeShouldReturnNull() {
        var orm = of(dataSource);
        NullableLocalTimeResult result = orm.query("SELECT CAST(NULL AS TIME)")
                .getSingleResult(NullableLocalTimeResult.class);
        assertNull(result.value());
    }

    // ORMTemplateImpl.findGenericType edge cases

    @Test
    public void findGenericTypeShouldResolveDeepInterfaceHierarchy() {
        var result = st.orm.core.template.impl.ORMTemplateImpl.findGenericType(
                DeepEntityRepo.class, st.orm.core.repository.EntityRepository.class, 0);
        assertTrue(result.isPresent());
        assertEquals(City.class, result.get());
    }

    interface MidLevelRepo extends st.orm.core.repository.EntityRepository<City, Integer> {}
    interface DeepEntityRepo extends MidLevelRepo {}

    // Simple class that does not implement EntityRepository at all.
    static class UnrelatedClass {}

    @Test
    public void findGenericTypeForNonMatchingInterfaceShouldReturnEmpty() {
        var result = st.orm.core.template.impl.ORMTemplateImpl.findGenericType(
                UnrelatedClass.class, st.orm.core.repository.EntityRepository.class, 0);
        assertTrue(result.isEmpty());
    }

    @Test
    public void findGenericTypeWithOutOfBoundsIndexShouldReturnEmpty() {
        var result = st.orm.core.template.impl.ORMTemplateImpl.findGenericType(
                MidLevelRepo.class, st.orm.core.repository.EntityRepository.class, 5);
        assertTrue(result.isEmpty());
    }

    // Single result error cases

    @Test
    public void getSingleResultWithNoResultsShouldThrow() {
        var orm = of(dataSource);
        assertThrows(PersistenceException.class,
                () -> orm.query("SELECT id, name FROM city WHERE id = -1").getSingleResult(City.class));
    }

    // Multiple results via getResultList

    @Test
    public void getResultListWithRecordTypeShouldReturnAllRows() {
        var orm = of(dataSource);
        List<City> cities = orm.query("SELECT id, name FROM city").getResultList(City.class);
        assertFalse(cities.isEmpty());
        assertTrue(cities.size() >= 6);
    }

    // Query with inline parameters

    @Test
    public void queryWithInlineParameterValuesShouldWork() {
        var orm = of(dataSource);
        record IdResult(Integer id) {}
        List<IdResult> results = orm.query(raw("SELECT id FROM city WHERE id = \0", 1)).getResultList(IdResult.class);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).id());
    }

    // DDL operations

    @Test
    public void ddlOperationsShouldWork() {
        var orm = of(dataSource);
        orm.query("CREATE TABLE IF NOT EXISTS test_ddl (id INT PRIMARY KEY)").executeUpdate();
        orm.query("DROP TABLE IF EXISTS test_ddl").executeUpdate();
    }
}
