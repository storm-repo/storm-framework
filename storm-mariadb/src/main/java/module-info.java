module storm.mariadb {
    uses st.orm.spi.EntityRepositoryProvider;
    requires storm;
    requires storm.mysql;
    requires jakarta.persistence;
    requires jakarta.annotation;
    provides st.orm.spi.EntityRepositoryProvider with st.orm.spi.mariadb.MariadbEntityRepositoryProviderImpl;
}