module storm.json {
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    requires com.fasterxml.jackson.databind;
    provides st.orm.core.spi.ORMConverterProvider with st.orm.json.spi.JsonORMConverterProviderImpl;
}
