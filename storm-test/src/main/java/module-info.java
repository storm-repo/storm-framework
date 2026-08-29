module storm.test {
    // st.orm.test.spring is deliberately not exported: the suites that use it run on the classpath, where the
    // ServiceLoader registration in their test resources picks it up; it is not API.
    exports st.orm.test;
    requires storm.core;
    requires static org.junit.jupiter.api;
    requires static spring.jdbc;
    requires java.sql;
    requires java.logging;
    requires static org.jspecify;
    // Testcontainers, behind StormTest.database(), is deliberately not required here: its jars split packages
    // between them, which no module graph accepts, so the module reads it from the classpath instead
    // (--add-reads storm.test=ALL-UNNAMED, see the build).
}
