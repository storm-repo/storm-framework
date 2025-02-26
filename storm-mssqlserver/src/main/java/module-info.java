import st.orm.spi.mssqlserver.MSSQLServerEntityRepositoryProviderImpl;
import st.orm.spi.mssqlserver.MSSQLServerSqlDialectProviderImpl;

module storm.mssqlserver {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with MSSQLServerEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with MSSQLServerSqlDialectProviderImpl;
}
