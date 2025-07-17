module storm.spring {
    requires storm;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires jakarta.annotation;
    requires org.reflections;
    requires spring.core;
    requires spring.aop;
    exports st.orm.spring;
}