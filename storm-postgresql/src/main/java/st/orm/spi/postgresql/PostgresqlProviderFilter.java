package st.orm.spi.postgresql;

import st.orm.spi.Provider;

import java.util.function.Predicate;

/**
 * Provider filter to select the PostgreSQL entity repository provider.
 */
public final class PostgresqlProviderFilter implements Predicate<Provider> {

    public static final PostgresqlProviderFilter INSTANCE = new PostgresqlProviderFilter();

    private PostgresqlProviderFilter() {
    }

    @Override
    public boolean test(Provider provider) {
        return provider instanceof PostgresqlEntityRepositoryProviderImpl;
    }
}
