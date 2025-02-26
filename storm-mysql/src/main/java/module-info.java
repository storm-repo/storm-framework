import st.orm.spi.mysql.MySQLEntityRepositoryProviderImpl;
import st.orm.spi.mysql.MySQLSqlDialectProviderImpl;

module storm.mysql {
    uses st.orm.spi.EntityRepositoryProvider;
    exports st.orm.spi.mysql to storm.mariadb;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with MySQLEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with MySQLSqlDialectProviderImpl;
}