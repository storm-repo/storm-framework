// The qualified export below targets storm-core, which depends on storm-foundation, so it is never observable
// while storm-foundation itself compiles; suppress the resulting "module not found" warning.
@SuppressWarnings("module")
module storm.foundation {
    exports st.orm;
    exports st.orm.mapping;
    exports st.orm.spi;
    // The engine reads the opaque foundation types through this package; to everyone else it is not API.
    exports st.orm.impl to storm.core;
    requires static jakarta.persistence;
    requires static org.jspecify;
    requires transitive java.sql;
}
