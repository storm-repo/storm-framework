module storm.serialization {
    exports st.orm.serialization;
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.serialization.core;
    requires kotlinx.serialization.json;
    provides st.orm.core.spi.ORMConverterProvider with st.orm.serialization.spi.JsonORMConverterProviderImpl;
}
