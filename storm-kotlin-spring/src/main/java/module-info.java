module storm.kotlin.spring {
    requires storm.kotlin;
    requires spring.jdbc;
    requires spring.tx;
    requires spring.context;
    requires spring.beans;
    requires spring.boot;
    requires kotlin.reflect;
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    requires jakarta.annotation;
    requires org.reflections;
    requires spring.core;
    requires spring.aop;
    requires org.slf4j;
    requires org.aspectj.weaver;
    requires java.logging;
    requires java.sql;
    requires storm.core;
    requires storm.foundation;
    requires spring.boot.autoconfigure;
    exports st.orm.kt.spring;
    opens st.orm.kt.spring.impl to kotlin.reflect;
    provides st.orm.core.spi.ConnectionProvider with st.orm.kt.spring.impl.SpringConnectionProviderImpl;
    provides st.orm.core.spi.TransactionTemplateProvider with st.orm.kt.spring.impl.SpringTransactionTemplateProviderImpl;
}