module storm.spring {
    requires storm.java21;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires jakarta.annotation;
    requires org.reflections;
    requires spring.core;
    requires spring.aop;
    requires org.slf4j;
    requires org.aspectj.weaver;
    requires java.logging;
    requires storm.core;
    requires storm.foundation;
    requires spring.boot.autoconfigure;
    exports st.orm.spring;
}