package st.orm.spi.sqlite;

import st.orm.tck.AbstractPolymorphicConformanceTest;
import st.orm.test.StormTest;

@StormTest(url = "jdbc:sqlite:target/conformance.db", scripts = "/data.sql")
public class SQLitePolymorphicConformanceTest extends AbstractPolymorphicConformanceTest {
}
