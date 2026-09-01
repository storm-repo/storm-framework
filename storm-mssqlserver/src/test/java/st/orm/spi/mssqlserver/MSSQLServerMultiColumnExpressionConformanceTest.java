package st.orm.spi.mssqlserver;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class MSSQLServerMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new MSSQLServerSqlDialect();
    }

    @Override
    protected boolean supportsRowValueIn() {
        return false;
    }

    @Override
    protected boolean supportsRowValueComparison() {
        return false;
    }
}
