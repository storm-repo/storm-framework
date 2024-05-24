package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;
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
    public KQuery template(@Nonnull TemplateFunction function) {
        return new KQueryImpl(ORM.template(function));
    }

    @Override
    public BindVars createBindVars() {
        return ORM.createBindVars();
    }

    @Override
    public <T extends Entity<ID>, ID> KEntityModel<T, ID> model(@Nonnull KClass<T> type) {
        //noinspection unchecked
        return new KEntityModel<>(ORM.model((Class<T>) REFLECTION.getType(type)));
    }

    @Override
    public <T extends Record> KQueryBuilder<T, T, Object> query(@Nonnull KClass<T> recordType) {
        //noinspection unchecked
        return new KQueryBuilderImpl<>(ORM.query((Class<T>) REFLECTION.getRecordType(recordType)));
    }

    @Override
    public KQuery process(StringTemplate stringTemplate) throws PersistenceException {
        return new KQueryImpl(ORM.process(stringTemplate));
    }
}
