package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PreparedQuery;
import st.orm.kotlin.KPreparedQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;

import java.util.stream.Stream;


public class KPreparedQueryImpl extends KQueryImpl implements KPreparedQuery {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final PreparedQuery preparedQuery;

    public KPreparedQueryImpl(@Nonnull PreparedQuery preparedQuery) {
        super(preparedQuery);
        this.preparedQuery = preparedQuery;
    }

    @Override
    public <ID> Stream<ID> getGeneratedKeys(@Nonnull KClass<ID> type) {
        //noinspection unchecked
        return preparedQuery.getGeneratedKeys((Class<ID>) REFLECTION.getType(type));
    }

    @Override
    public void close() {
        preparedQuery.close();
    }
}
