package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.Lazy;
import st.orm.PersistenceException;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.repository.KModel;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ORMTemplate;

import static java.util.Objects.requireNonNull;


public class KORMTemplateImpl implements KORMTemplate {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final ORMTemplate orm;

    public KORMTemplateImpl(@Nonnull ORMTemplate orm) {
        this.orm = requireNonNull(orm, "orm");
    }

    @Override
    public BindVars createBindVars() {
        return orm.createBindVars();
    }

    @Override
    public <T extends Record, ID> Lazy<T, ID> lazy(@Nonnull Class<T> type, @Nullable ID pk) {
        return orm.lazy(type, pk);
    }

    @Override
    public <T extends Record, ID> KModel<T, ID> model(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return new KModel<>(orm.model((Class<T>) REFLECTION.getType(type)));
    }

    @Override
    public <T extends Record> KQueryBuilder<T, T, Object> selectFrom(@Nonnull KClass<T> fromType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(orm.selectFrom((Class<T>) REFLECTION.getRecordType(fromType)));
    }

    @Override
    public <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType, KClass<R> selectType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(orm.selectFrom((Class<T>) REFLECTION.getRecordType(fromType), (Class<R>) REFLECTION.getType(selectType)));
    }

    @Override
    public <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@Nonnull KClass<T> fromType, KClass<R> selectType, @Nonnull StringTemplate template) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(orm.selectFrom((Class<T>) REFLECTION.getRecordType(fromType), (Class<R>) REFLECTION.getType(selectType), template));
    }

    @Override
    public KQuery query(@Nonnull StringTemplate template) throws PersistenceException {
        return new KQueryImpl(orm.query(template));
    }
}
