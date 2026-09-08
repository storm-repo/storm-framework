module storm.foundation {
    exports st.orm;
    exports st.orm.mapping;
    exports st.orm.spi;
    requires static jakarta.persistence;
    requires static org.jspecify;
    requires transitive java.sql;
}
