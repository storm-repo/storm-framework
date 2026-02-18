module storm.test {
    exports st.orm.test;
    requires storm.core;
    requires static org.junit.jupiter.api;
    requires java.sql;
    requires java.logging;
}
