module storm.spring {
    requires storm.java21;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires jakarta.annotation;
    requires spring.core;
    requires spring.aop;
    requires org.slf4j;
    requires org.aspectj.weaver;
    requires java.logging;
    requires storm.core;
    requires storm.foundation;
    requires spring.boot.autoconfigure;
    requires java.sql;
    exports st.orm.spring;
    exports st.orm.spring.impl;
    provides st.orm.core.spi.ConnectionProvider with st.orm.spring.impl.TransactionAwareConnectionProviderImpl;
}