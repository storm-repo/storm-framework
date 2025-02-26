import st.orm.spi.sqlserver.SqlserverEntityRepositoryProviderImpl;
import st.orm.spi.sqlserver.SqlserverSqlDialectProviderImpl;

module storm.sqlserver {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with SqlserverEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with SqlserverSqlDialectProviderImpl;
}
