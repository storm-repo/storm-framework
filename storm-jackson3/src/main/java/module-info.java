module storm.jackson3 {
    exports st.orm.jackson;
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    requires tools.jackson.databind;
    provides st.orm.core.spi.ORMConverterProvider with st.orm.jackson.spi.JsonORMConverterProviderImpl;
}
