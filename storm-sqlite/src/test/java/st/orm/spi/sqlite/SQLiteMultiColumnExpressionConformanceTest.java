package st.orm.spi.sqlite;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class SQLiteMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new SQLiteSqlDialect();
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
