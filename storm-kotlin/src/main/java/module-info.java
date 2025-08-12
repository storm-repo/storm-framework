module storm.kotlin {
    requires storm.foundation;
    requires storm.core;
    exports st.orm.template;
    exports st.orm.repository;
    exports st.orm.template.impl to kotlin.reflect;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    uses st.orm.core.spi.ORMReflection;
    provides st.orm.core.spi.ORMReflectionProvider with st.orm.spi.ORMReflectionProviderImpl;
    provides st.orm.core.spi.ConnectionProvider with st.orm.template.impl.CoroutineAwareConnectionProviderImpl;
    provides st.orm.core.spi.TransactionTemplateProvider with st.orm.template.impl.TransactionTemplateProviderImpl;
}