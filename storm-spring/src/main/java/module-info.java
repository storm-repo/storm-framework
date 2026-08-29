// The qualified exports below target sibling modules that depend on storm-spring, so they are never
// observable while storm-spring itself compiles; suppress the resulting "module not found" warnings.
@SuppressWarnings("module")
module storm.spring {
    requires static storm.java;
    requires static storm.micrometer;
    requires static micrometer.observation;
    requires static micrometer.tracing;
    requires static micrometer.commons;
    requires spring.jdbc;
    requires static spring.orm;
    requires static jakarta.persistence;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires static org.jspecify;
    requires spring.core;
    requires spring.aop;
    requires static spring.web;
    requires static jakarta.servlet;
    requires static context.propagation;
    requires static spring.boot.actuator;
    requires org.slf4j;
    requires org.aspectj.weaver;
    requires java.logging;
    requires storm.core;
    requires storm.foundation;
    requires spring.boot.autoconfigure;
    requires java.sql;
    exports st.orm.spring;
    exports st.orm.spring.boot;
    // The impl package is reachable by Storm's own modules and by the Spring modules that reflectively
    // instantiate its auto-configuration, registrar and runtime-hints classes; to everyone else it is not API.
    exports st.orm.spring.impl to
            storm.kotlin.spring,
            spring.beans,
            spring.boot,
            spring.boot.autoconfigure,
            spring.context,
            spring.core;
    provides st.orm.core.spi.ExternalTransactionProvider with st.orm.spring.SpringExternalTransactionProvider;
}
