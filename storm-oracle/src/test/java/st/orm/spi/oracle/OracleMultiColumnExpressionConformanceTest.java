package st.orm.spi.oracle;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class OracleMultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new OracleSqlDialect();
    }
}
