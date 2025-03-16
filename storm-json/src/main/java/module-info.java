module storm.json {
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    requires com.fasterxml.jackson.databind;
    provides st.orm.spi.ORMConverterProvider with st.orm.json.spi.JsonORMConverterProviderImpl;
}
