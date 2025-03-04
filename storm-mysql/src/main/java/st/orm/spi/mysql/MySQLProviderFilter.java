package st.orm.spi.mysql;

import st.orm.spi.Provider;
import st.orm.spi.SqlDialectProvider;

import java.util.function.Predicate;

/**
 * Provider filter to select the MariaDB entity repository provider.
 */
public final class MySQLProviderFilter implements Predicate<Provider> {

    public static final MySQLProviderFilter INSTANCE = new MySQLProviderFilter();

    private MySQLProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        if (!(provider instanceof SqlDialectProvider)) {
            // Only filter providers that implement the SqlDialectProvider interface.
            return true;
        }
        return provider instanceof MySQLEntityRepositoryProviderImpl;
    }
}
