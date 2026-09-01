package st.orm.spi.h2;

import st.orm.core.template.SqlDialect;
import st.orm.tck.AbstractMultiColumnExpressionConformanceTest;

public class H2MultiColumnExpressionConformanceTest extends AbstractMultiColumnExpressionConformanceTest {

    @Override
    protected SqlDialect dialect() {
        return new H2SqlDialect();
    }
}
