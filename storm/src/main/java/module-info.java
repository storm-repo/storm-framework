module storm {
    uses st.orm.spi.ORMReflectionProvider;
    uses st.orm.spi.ORMConverterProvider;
    uses st.orm.spi.EntityRepositoryProvider;
    uses st.orm.spi.ProjectionRepositoryProvider;
    uses st.orm.spi.QueryBuilderProvider;
    uses st.orm.spi.SqlDialectProvider;
    exports st.orm;
    exports st.orm.repository;
    exports st.orm.template;
    exports st.orm.template.impl;
    exports st.orm.spi;
    requires java.sql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    requires java.compiler;
    requires org.jetbrains.annotations;
    provides st.orm.spi.ORMReflectionProvider with st.orm.spi.DefaultORMReflectionProviderImpl;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.DefaultEntityRepositoryProviderImpl;
    provides st.orm.spi.ProjectionRepositoryProvider with st.orm.spi.DefaultProjectionRepositoryProviderImpl;
    provides st.orm.spi.QueryBuilderProvider with st.orm.spi.DefaultQueryBuilderProviderImpl;
    provides st.orm.spi.SqlDialectProvider with st.orm.spi.DefaultSqlDialectProviderImpl;
}