module storm.spring {
    requires storm;
    requires storm.kotlin;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires jakarta.annotation;
    requires org.reflections;
    exports st.orm.spring;
}