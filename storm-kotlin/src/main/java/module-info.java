module storm.kotlin {
    requires storm;
    exports st.orm.kotlin;
    exports st.orm.kotlin.template;
    exports st.orm.kotlin.repository;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires org.jetbrains.annotations;
    uses st.orm.spi.ORMReflection;
    provides st.orm.spi.ORMReflectionProvider with st.orm.kotlin.spi.KORMReflectionProviderImpl;
}