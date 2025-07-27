module storm.kt {
    requires storm.foundation;
    requires storm.core;
    exports st.orm.kt.template;
    exports st.orm.kt.repository;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    uses st.orm.core.spi.ORMReflection;
    provides st.orm.core.spi.ORMReflectionProvider with st.orm.kt.spi.ORMReflectionProviderImpl;
}