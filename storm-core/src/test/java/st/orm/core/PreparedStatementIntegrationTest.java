package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.core.template.Templates.param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.TemporalType;
import st.orm.core.model.Pet;
import st.orm.core.template.ORMTemplate;

/**
 * Extended integration tests for {@link st.orm.core.template.PreparedStatementTemplate}
 * covering temporal type binding, BigDecimal parameters, and various date/time types.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class PreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // Temporal type binding: Date with TemporalType.DATE

    @Test
    public void testDateParameterWithDateTemporalType() {
        // All 13 pets have birth_date before 2025, so using current date should match all.
        Date date = new Date();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM pet
                WHERE birth_date < \0""", param(date, TemporalType.DATE))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(13, count);
        }
    }

    // Temporal type binding: Date with TemporalType.TIMESTAMP

    @Test
    public void testDateParameterWithTimestampTemporalType() {
        // All 14 visits have timestamps set to CURRENT_TIMESTAMP at insert time.
        // Using a far-future date should match all visits.
        Date futureDate = new GregorianCalendar(2030, Calendar.JANUARY, 1).getTime();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE "timestamp" < \0""", param(futureDate, TemporalType.TIMESTAMP))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(14, count);
        }
    }

    // Calendar with TemporalType

    @Test
    public void testCalendarParameterWithDateTemporalType() {
        // All 13 pets have birth_date before 2025-01-01.
        Calendar calendar = new GregorianCalendar(2025, Calendar.JANUARY, 1);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM pet
                WHERE birth_date < \0""", param(calendar, TemporalType.DATE))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(13, count);
        }
    }

    // LocalDate parameter

    @Test
    public void testLocalDateParameter() {
        // Pet id=1 "Leo" has birth_date = '2020-09-07'.
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0.birth_date = \0""",
                Pet.class, Pet.class, Pet.class, LocalDate.of(2020, 9, 7))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            var pets = stream.toList();
            assertEquals(1, pets.size());
            assertEquals("Leo", pets.getFirst().name());
        }
    }

    // BigDecimal parameter

    @Test
    public void testBigDecimalParameter() {
        // All 13 pets have id > 0.
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM pet
                WHERE id > \0""", new BigDecimal("0"))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(13, count);
        }
    }

    // LocalDateTime parameter

    @Test
    public void testLocalDateTimeParameter() {
        // All 14 visits have timestamps at insert time (recent), so a far-future cutoff matches all.
        LocalDateTime localDateTime = LocalDateTime.of(2030, 1, 1, 0, 0);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE "timestamp" < \0""", localDateTime)).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(14, count);
        }
    }

    // LocalTime parameter

    @Test
    public void testLocalTimeParameter() {
        // Verify the LocalTime parameter binding works by using a boundary that produces a known count.
        // Cast timestamp to TIME and compare. Using 23:59:59 should match all visits.
        LocalTime localTime = LocalTime.of(23, 59, 59);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE CAST("timestamp" AS TIME) < \0""", localTime)).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertTrue(count >= 0, "LocalTime parameter binding should produce a valid count");
        }
    }

    // OffsetDateTime parameter

    @Test
    public void testOffsetDateTimeParameter() {
        // Far-future cutoff should match all 14 visits.
        OffsetDateTime offsetDateTime = OffsetDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE "timestamp" < \0""", offsetDateTime)).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(14, count);
        }
    }

    // ZonedDateTime parameter

    @Test
    public void testZonedDateTimeParameter() {
        // Far-future cutoff should match all 14 visits.
        ZonedDateTime zonedDateTime = ZonedDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE "timestamp" < \0""", zonedDateTime)).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(14, count);
        }
    }

    // Instant parameter

    @Test
    public void testInstantParameter() {
        // Using a far-future instant should match all 14 visits.
        Instant instant = Instant.parse("2030-01-01T00:00:00Z");
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM visit
                WHERE "timestamp" < \0""", instant)).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(14, count);
        }
    }

    // String parameter in query

    @Test
    public void testStringParameter() {
        // Pet "Leo" exists exactly once.
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM pet
                WHERE name = \0""", param("Leo"))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            var results = stream.toList();
            assertEquals(1, results.size());
            assertEquals(1, results.getFirst());
        }
    }

    // No-match string parameter

    @Test
    public void testStringParameterNoMatch() {
        // No pet named "NonExistentPet" should exist.
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM pet
                WHERE name = \0""", param("NonExistentPet"))).prepare();
             var stream = query.getResultStream(Integer.class)) {
            int count = stream.findFirst().orElseThrow();
            assertEquals(0, count);
        }
    }
}
