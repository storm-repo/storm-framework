package st.orm.spi.postgresql;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class PostgreSQLMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new PostgreSQLSqlDialect();
    }
}
