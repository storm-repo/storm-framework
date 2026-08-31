// The qualified export below targets a sibling module that depends on storm-java21, so it is never observable
// while storm-java21 itself compiles; suppress the resulting "module not found" warning.
@SuppressWarnings("module")
module storm.java {
    exports st.orm.repository;
    exports st.orm.template;
    // The impl package is reachable by Storm's Spring integration, which composes the engine builder and wraps it
    // in the facade builder; to everyone else it is not API.
    exports st.orm.template.impl to storm.spring;
    requires java.sql;
    requires static jakarta.persistence;
    requires static org.jspecify;
    requires java.compiler;
    requires transitive storm.foundation;
    requires storm.core;
}
