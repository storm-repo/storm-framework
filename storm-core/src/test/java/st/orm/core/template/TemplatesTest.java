package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Element;
import st.orm.TemporalType;
import st.orm.core.model.City;
import st.orm.core.template.impl.Elements;

/**
 * Tests for {@link Templates} static factory methods.
 */
public class TemplatesTest {

    @Test
    public void testSelect() {
        assertNotNull(Templates.select(City.class));
    }

    @Test
    public void testFromWithAutoJoin() {
        assertNotNull(Templates.from(City.class, true));
    }

    @Test
    public void testInsert() {
        assertNotNull(Templates.insert(City.class));
    }

    @Test
    public void testValues() {
        City city = new City(null, "Test");
        assertNotNull(Templates.values(city));
    }

    @Test
    public void testValuesWithIterable() {
        City city1 = new City(null, "A");
        City city2 = new City(null, "B");
        Element element = Templates.values(List.of(city1, city2));
        assertNotNull(element);
        assertInstanceOf(Elements.Values.class, element);
    }

    @Test
    public void testValuesWithIterableAndIgnoreAutoGenerate() {
        City city1 = new City(1, "A");
        City city2 = new City(2, "B");
        Element element = Templates.values(List.of(city1, city2), true);
        assertNotNull(element);
        assertInstanceOf(Elements.Values.class, element);
    }

    @Test
    public void testNamedParam() {
        Element element = Templates.param("status", 42);
        assertNotNull(element);
        assertInstanceOf(Elements.Param.class, element);
    }

    @Test
    public void testNamedParamNullName() {
        assertThrows(NullPointerException.class, () -> Templates.param((String) null, 42));
    }

    @Test
    public void testParamWithConverter() {
        Element element = Templates.param("hello", v -> v.toUpperCase());
        assertNotNull(element);
        assertInstanceOf(Elements.Param.class, element);
    }

    @Test
    public void testNamedParamWithConverter() {
        Element element = Templates.param("myParam", "hello", v -> v.toUpperCase());
        assertNotNull(element);
        assertInstanceOf(Elements.Param.class, element);
    }

    @Test
    public void testNamedParamWithNullConverter() {
        assertThrows(NullPointerException.class, () -> Templates.param("myParam", "hello", null));
    }

    @Test
    public void testDateParamWithDateTemporalType() {
        Date date = new Date();
        Element element = Templates.param(date, TemporalType.DATE);
        assertNotNull(element);
    }

    @Test
    public void testDateParamWithTimeTemporalType() {
        Date date = new Date();
        Element element = Templates.param(date, TemporalType.TIME);
        assertNotNull(element);
    }

    @Test
    public void testDateParamWithTimestampTemporalType() {
        Date date = new Date();
        Element element = Templates.param(date, TemporalType.TIMESTAMP);
        assertNotNull(element);
    }

    @Test
    public void testNamedDateParamWithDateTemporalType() {
        Date date = new Date();
        Element element = Templates.param("eventDate", date, TemporalType.DATE);
        assertNotNull(element);
    }

    @Test
    public void testNamedDateParamWithTimeTemporalType() {
        Date date = new Date();
        Element element = Templates.param("eventTime", date, TemporalType.TIME);
        assertNotNull(element);
    }

    @Test
    public void testNamedDateParamWithTimestampTemporalType() {
        Date date = new Date();
        Element element = Templates.param("eventTs", date, TemporalType.TIMESTAMP);
        assertNotNull(element);
    }

    @Test
    public void testCalendarParamWithDateTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15);
        Element element = Templates.param(calendar, TemporalType.DATE);
        assertNotNull(element);
    }

    @Test
    public void testCalendarParamWithTimeTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15, 10, 30, 0);
        Element element = Templates.param(calendar, TemporalType.TIME);
        assertNotNull(element);
    }

    @Test
    public void testCalendarParamWithTimestampTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15, 10, 30, 0);
        Element element = Templates.param(calendar, TemporalType.TIMESTAMP);
        assertNotNull(element);
    }

    @Test
    public void testNamedCalendarParamWithDateTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15);
        Element element = Templates.param("eventDate", calendar, TemporalType.DATE);
        assertNotNull(element);
    }

    @Test
    public void testNamedCalendarParamWithTimeTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15, 10, 30, 0);
        Element element = Templates.param("eventTime", calendar, TemporalType.TIME);
        assertNotNull(element);
    }

    @Test
    public void testNamedCalendarParamWithTimestampTemporalType() {
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 15, 10, 30, 0);
        Element element = Templates.param("eventTs", calendar, TemporalType.TIMESTAMP);
        assertNotNull(element);
    }

    @Test
    public void testUnsafe() {
        Element element = Templates.unsafe("1 = 1");
        assertNotNull(element);
        assertInstanceOf(Elements.Unsafe.class, element);
    }

    @Test
    public void testUpdate() {
        Element element = Templates.update(City.class);
        assertNotNull(element);
        assertInstanceOf(Elements.Update.class, element);
    }

    @Test
    public void testDelete() {
        Element element = Templates.delete(City.class);
        assertNotNull(element);
        assertInstanceOf(Elements.Delete.class, element);
    }

    @Test
    public void testSetWithData() {
        City city = new City(1, "Updated");
        Element element = Templates.set(city);
        assertNotNull(element);
        assertInstanceOf(Elements.Set.class, element);
    }

    @Test
    public void testWhereWithIterable() {
        Element element = Templates.where(List.of(1, 2, 3));
        assertNotNull(element);
        assertInstanceOf(Elements.Where.class, element);
    }

    @Test
    public void testWhereWithObject() {
        Element element = Templates.where(42);
        assertNotNull(element);
        assertInstanceOf(Elements.Where.class, element);
    }

    @Test
    public void testTableWithAlias() {
        Element element = Templates.table(City.class, "c");
        assertNotNull(element);
        assertInstanceOf(Elements.Table.class, element);
    }

    @Test
    public void testAlias() {
        Element element = Templates.alias(City.class);
        assertNotNull(element);
        assertInstanceOf(Elements.Alias.class, element);
    }
}
