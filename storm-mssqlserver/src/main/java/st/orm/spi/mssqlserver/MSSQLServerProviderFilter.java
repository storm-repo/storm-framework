package st.orm.spi.mssqlserver;

import st.orm.spi.Provider;
import st.orm.spi.SqlDialectProvider;

import java.util.function.Predicate;

/**
 * Provider filter to select the SQL Server entity repository provider.
 */
public final class MSSQLServerProviderFilter implements Predicate<Provider> {

    public static final MSSQLServerProviderFilter INSTANCE = new MSSQLServerProviderFilter();

    private MSSQLServerProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        if (!(provider instanceof SqlDialectProvider)) {
            // Only filter providers that implement the SqlDialectProvider interface.
            return true;
        }
        return provider instanceof MSSQLServerEntityRepositoryProviderImpl;
    }
}
