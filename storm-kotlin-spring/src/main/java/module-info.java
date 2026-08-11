module storm.kotlin.spring {
    requires storm.kotlin;
    requires transitive storm.spring;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires spring.core;
    requires spring.aop;
    requires org.slf4j;
    requires org.aspectj.weaver;
    requires java.logging;
    requires java.sql;
    requires storm.core;
    requires storm.foundation;
    requires spring.boot.autoconfigure;
    exports st.orm.spring.kotlin;
}
