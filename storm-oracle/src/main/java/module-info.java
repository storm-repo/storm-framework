module storm.oracle {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires jakarta.annotation;
    requires java.sql;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.oracle.OracleEntityRepositoryProviderImpl;
    provides st.orm.spi.SqlDialectProvider with st.orm.spi.oracle.OracleSqlDialectProviderImpl;
}
