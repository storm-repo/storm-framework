module storm.jackson {
    exports st.orm.jackson;
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    requires com.fasterxml.jackson.databind;
    provides st.orm.core.spi.ORMConverterProvider with st.orm.jackson.spi.JsonORMConverterProviderImpl;
}
