import st.orm.jackson.JsonORMConverterProviderImpl;

module storm.jackson {
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    requires com.fasterxml.jackson.databind;
    provides st.orm.core.spi.ORMConverterProvider with JsonORMConverterProviderImpl;
}
