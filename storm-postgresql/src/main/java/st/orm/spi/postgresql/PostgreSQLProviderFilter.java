package st.orm.spi.postgresql;

import st.orm.spi.Provider;

import java.util.function.Predicate;

/**
 * Provider filter to select the PostgreSQL entity repository provider.
 */
public final class PostgreSQLProviderFilter implements Predicate<Provider> {

    public static final PostgreSQLProviderFilter INSTANCE = new PostgreSQLProviderFilter();

    private PostgreSQLProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        return provider instanceof PostgreSQLEntityRepositoryProviderImpl;
    }
}
