package st.orm.kt.spi;

import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.ORMReflectionProvider;

public class ORMReflectionProviderImpl implements ORMReflectionProvider {

    @Override
    public ORMReflection getReflection() {
        return new ORMReflectionImpl();
    }
}
