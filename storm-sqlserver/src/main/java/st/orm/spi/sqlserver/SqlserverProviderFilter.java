package st.orm.spi.sqlserver;

import st.orm.spi.Provider;

import java.util.function.Predicate;

/**
 * Provider filter to select the SQL Server entity repository provider.
 */
public final class SqlserverProviderFilter implements Predicate<Provider> {

    public static final SqlserverProviderFilter INSTANCE = new SqlserverProviderFilter();

    private SqlserverProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        return provider instanceof SqlserverEntityRepositoryProviderImpl;
    }
}
