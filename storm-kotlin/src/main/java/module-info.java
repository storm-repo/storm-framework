module storm.kotlin {
    requires storm.foundation;
    requires storm.core;
    exports st.orm.template;
    exports st.orm.repository;
    exports st.orm.template.impl to kotlin.reflect;
    requires java.sql;
    requires static jakarta.persistence;
    requires static org.jspecify;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires org.slf4j;
    requires org.jetbrains.annotations;
    uses st.orm.core.spi.ORMReflection;
    provides st.orm.core.spi.ORMReflectionProvider with st.orm.spi.ORMReflectionProviderImpl;
}
