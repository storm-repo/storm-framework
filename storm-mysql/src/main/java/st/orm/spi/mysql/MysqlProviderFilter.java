package st.orm.spi.mysql;

import st.orm.spi.Provider;

import java.util.function.Predicate;

/**
 * Provider filter to select the MariaDB entity repository provider.
 */
public final class MysqlProviderFilter implements Predicate<Provider> {

    public static final MysqlProviderFilter INSTANCE = new MysqlProviderFilter();

    private MysqlProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        return provider instanceof MysqlEntityRepositoryProviderImpl;
    }
}
