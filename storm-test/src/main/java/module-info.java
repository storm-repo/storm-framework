module storm.test {
    exports st.orm.test;
    requires storm.core;
    requires static org.junit.jupiter.api;
    requires java.sql;
    requires java.logging;
    requires static org.jspecify;
    // Testcontainers, behind StormTest.database(), is deliberately not required here: its jars split packages
    // between them, which no module graph accepts, so the module reads it from the classpath instead
    // (--add-reads storm.test=ALL-UNNAMED, see the build).
}
