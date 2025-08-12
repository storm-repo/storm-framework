module storm.core {
    uses st.orm.core.spi.ORMReflectionProvider;
    uses st.orm.core.spi.ORMConverterProvider;
    uses st.orm.core.spi.EntityRepositoryProvider;
    uses st.orm.core.spi.ProjectionRepositoryProvider;
    uses st.orm.core.spi.QueryBuilderProvider;
    uses st.orm.core.spi.SqlDialectProvider;
    uses st.orm.core.spi.ConnectionProvider;
    uses st.orm.core.spi.TransactionTemplateProvider;
    exports st.orm.core.template;
    exports st.orm.core.template.impl;
    exports st.orm.core.spi;
    exports st.orm.core.repository;
    exports st.orm.core.repository.impl;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires java.compiler;
    requires storm.foundation;
    requires org.slf4j;
    provides st.orm.core.spi.ORMReflectionProvider with st.orm.core.spi.DefaultORMReflectionProviderImpl;
    provides st.orm.core.spi.EntityRepositoryProvider with st.orm.core.spi.DefaultEntityRepositoryProviderImpl;
    provides st.orm.core.spi.ProjectionRepositoryProvider with st.orm.core.spi.DefaultProjectionRepositoryProviderImpl;
    provides st.orm.core.spi.QueryBuilderProvider with st.orm.core.spi.DefaultQueryBuilderProviderImpl;
    provides st.orm.core.spi.SqlDialectProvider with st.orm.core.spi.DefaultSqlDialectProviderImpl;
    provides st.orm.core.spi.ConnectionProvider with st.orm.core.spi.DefaultConnectionProviderImpl;
    provides st.orm.core.spi.TransactionTemplateProvider with st.orm.core.spi.DefaultTransactionTemplateProviderImpl;
}