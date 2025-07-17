module storm.mssqlserver {
    uses st.orm.core.spi.EntityRepositoryProvider;
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.core.spi.EntityRepositoryProvider with st.orm.spi.mssqlserver.MSSQLServerEntityRepositoryProviderImpl;
    provides st.orm.core.spi.SqlDialectProvider with st.orm.spi.mssqlserver.MSSQLServerSqlDialectProviderImpl;
}
