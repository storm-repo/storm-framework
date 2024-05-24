package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.Query;
import st.orm.kotlin.KPreparedQuery;
import st.orm.kotlin.KQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class KQueryImpl implements KQuery {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final Query query;

    public KQueryImpl(@Nonnull Query query) {
        this.query = requireNonNull(query, "query");
    }

    @Override
    public KPreparedQuery prepare() {
        return new KPreparedQueryImpl(query.prepare());
    }

    @Override
    public Stream<Object[]> getResultStream() {
        return query.getResultStream();
    }

    @Override
    public <T> Stream<T> getResultStream(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return query.getResultStream((Class<T>) REFLECTION.getType(type));
    }

    @Override
    public int executeUpdate() {
        return query.executeUpdate();
    }

    @Override
    public int[] executeBatch() {
        return query.executeBatch();
    }
}
