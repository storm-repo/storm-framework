module storm.postgresql {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.postgresql.PostgreSQLEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with st.orm.spi.postgresql.PostgreSQLSqlDialectProviderImpl;
}