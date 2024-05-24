import st.orm.json.spi.JsonORMConverterProviderImpl;

module storm.json {
    requires storm;
    requires jakarta.annotation;
    requires com.fasterxml.jackson.databind;
    provides st.orm.spi.ORMConverterProvider with JsonORMConverterProviderImpl;
}
