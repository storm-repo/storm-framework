module storm.mariadb {
    uses st.orm.core.spi.EntityRepositoryProvider;
    requires storm.foundation;
    requires storm.core;
    requires storm.mysql;
    requires static org.jspecify;
    provides st.orm.core.spi.EntityRepositoryProvider with st.orm.spi.mariadb.MariaDBEntityRepositoryProviderImpl;
    provides st.orm.core.spi.SqlDialectProvider with st.orm.spi.mariadb.MariaDBSqlDialectProviderImpl;
}
