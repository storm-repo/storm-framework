module storm.java21 {
    exports st.orm.repository;
    exports st.orm.template;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires java.compiler;
    requires storm.foundation;
    requires storm.core;
}