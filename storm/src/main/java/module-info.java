module storm {
    uses st.orm.spi.ORMReflectionProvider;
    uses st.orm.spi.ORMConverterProvider;
    uses st.orm.spi.EntityRepositoryProvider;
    exports st.orm;
    exports st.orm.repository;
    exports st.orm.template;
    exports st.orm.template.impl;
    exports st.orm.spi;
    requires jakarta.persistence;
    requires jakarta.annotation;
    provides st.orm.spi.ORMReflectionProvider with st.orm.spi.DefaultORMReflectionProviderImpl;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.DefaultEntityRepositoryProviderImpl;
}