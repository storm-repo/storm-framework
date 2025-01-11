module storm.mysql {
    uses st.orm.spi.EntityRepositoryProvider;
    exports st.orm.spi.mysql to storm.mariadb;
    requires storm;
    requires jakarta.annotation;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.mysql.MysqlEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with st.orm.spi.mysql.MysqlSqlDialectProviderImpl;
}