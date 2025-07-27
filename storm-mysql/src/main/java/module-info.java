module storm.mysql {
    uses st.orm.core.spi.EntityRepositoryProvider;
    exports st.orm.spi.mysql;
    requires storm.foundation;
    requires storm.core;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.core.spi.EntityRepositoryProvider with st.orm.spi.mysql.MySQLEntityRepositoryProviderImpl;
    provides st.orm.core.spi.SqlDialectProvider with st.orm.spi.mysql.MySQLSqlDialectProviderImpl;
}