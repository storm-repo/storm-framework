import st.orm.spi.postgresql.PostgreSQLEntityRepositoryProviderImpl;
import st.orm.spi.postgresql.PostgreSQLSqlDialectProviderImpl;

module storm.postgresql {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with PostgreSQLEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with PostgreSQLSqlDialectProviderImpl;
}