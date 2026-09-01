package st.orm.spi.mariadb;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class MariaDBMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new MariaDBSqlDialect();
    }

    @Override
    protected boolean supportsRowValueComparison() {
        return false;
    }
}
