package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
import org.jetbrains.annotations.NotNull;
import st.orm.BindVars;
import st.orm.kotlin.KQuery;
import st.orm.kotlin.repository.KEntityModel;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.KQueryBuilder;
import st.orm.repository.Entity;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ORMTemplate;
import st.orm.template.TemplateFunction;

import static java.util.Objects.requireNonNull;


public class KORMTemplateImpl implements KORMTemplate {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final ORMTemplate ORM;

    public KORMTemplateImpl(@Nonnull ORMTemplate orm) {
        this.ORM = requireNonNull(orm, "orm");
    }

    @Override
    public BindVars createBindVars() {
        return ORM.createBindVars();
    }

    @Override
    public <T extends Record & Entity<ID>, ID> KEntityModel<T, ID> model(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return new KEntityModel<>(ORM.model((Class<T>) REFLECTION.getType(type)));
    }

    @Override
    public <T extends Record> KQueryBuilder<T, T, Object> selectFrom(@Nonnull KClass<T> fromType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(ORM.selectFrom((Class<T>) REFLECTION.getRecordType(fromType)));
    }

    @Override
    public <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@NotNull Class<T> fromType, Class<R> selectType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(ORM.selectFrom((Class<T>) REFLECTION.getRecordType(fromType), selectType));
    }

    @Override
    public <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@NotNull Class<T> fromType, Class<R> selectType, @NotNull StringTemplate template) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(ORM.selectFrom((Class<T>) REFLECTION.getRecordType(fromType), selectType, template));
    }

    @Override
    public <T extends Record, R> KQueryBuilder<T, R, Object> selectFrom(@NotNull Class<T> fromType, Class<R> selectType, @NotNull TemplateFunction templateFunction) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(ORM.selectFrom((Class<T>) REFLECTION.getRecordType(fromType), selectType, templateFunction));
    }

    @Override
    public KQuery query(@Nonnull StringTemplate stringTemplate) throws PersistenceException {
        return new KQueryImpl(ORM.query(stringTemplate));
    }

    @Override
    public KQuery query(@Nonnull TemplateFunction function) {
        return new KQueryImpl(ORM.query(function));
    }
}
