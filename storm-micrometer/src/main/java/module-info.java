module storm.micrometer {
    exports st.orm.micrometer;
    requires storm.foundation;
    requires storm.core;
    requires micrometer.observation;
    requires static micrometer.tracing;
    requires micrometer.commons;
    requires static org.jspecify;
}
