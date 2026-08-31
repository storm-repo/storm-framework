package st.orm.template.impl;

import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.ORMReflectionProvider;

public class ORMReflectionProviderImpl implements ORMReflectionProvider {

    @Override
    public ORMReflection getReflection() {
        return new ORMReflectionImpl();
    }
}
