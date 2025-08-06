module storm.kotlin {
    requires storm.foundation;
    requires storm.core;
    exports st.orm.kt.template;
    exports st.orm.kt.repository;
    exports st.orm.kt.template.impl to kotlin.reflect;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    uses st.orm.core.spi.ORMReflection;
    provides st.orm.core.spi.ORMReflectionProvider with st.orm.kt.spi.ORMReflectionProviderImpl;
    provides st.orm.core.spi.ConnectionProvider with st.orm.kt.template.impl.TransactionAwareConnectionProviderImpl;
    provides st.orm.core.spi.TransactionTemplateProvider with st.orm.kt.template.impl.TransactionTemplateProviderImpl;
}