import st.orm.spi.mariadb.MariaDBEntityRepositoryProviderImpl;
import st.orm.spi.mariadb.MariaDBSqlDialectProviderImpl;

module storm.mariadb {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires storm.mysql;
    requires jakarta.annotation;
    provides st.orm.spi.EntityRepositoryProvider with MariaDBEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with MariaDBSqlDialectProviderImpl;
}