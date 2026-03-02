package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import st.orm.core.model.City;

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
}
