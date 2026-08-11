module storm.foundation {
    exports st.orm;
    exports st.orm.mapping;
    requires static jakarta.persistence;
    requires static org.jspecify;
    requires java.sql;
}
