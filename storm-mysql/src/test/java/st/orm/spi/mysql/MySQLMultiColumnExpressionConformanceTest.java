package st.orm.spi.mysql;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class MySQLMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new MySQLSqlDialect();
    }

    @Override
    protected boolean supportsRowValueComparison() {
        return false;
    }
}
