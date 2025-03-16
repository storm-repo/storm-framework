module storm.mysql {
    uses st.orm.spi.EntityRepositoryProvider;
    exports st.orm.spi.mysql;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.mysql.MySQLEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with st.orm.spi.mysql.MySQLSqlDialectProviderImpl;
}