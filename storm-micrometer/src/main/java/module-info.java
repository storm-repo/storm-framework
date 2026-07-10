module storm.micrometer {
    exports st.orm.micrometer;
    requires storm.foundation;
    requires storm.core;
    requires micrometer.observation;
    requires micrometer.commons;
    requires jakarta.annotation;
}
