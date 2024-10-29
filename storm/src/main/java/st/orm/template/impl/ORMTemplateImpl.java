/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.FK;
import st.orm.Lazy;
import st.orm.Name;
import st.orm.PK;
import st.orm.Persist;
import st.orm.Query;
import st.orm.Version;
import st.orm.repository.Column;
import st.orm.repository.Entity;
import st.orm.repository.EntityModel;
import st.orm.spi.EntityRepositoryProvider;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;
import st.orm.template.ColumnNameResolver;
import st.orm.template.ForeignKeyResolver;
import st.orm.template.ORMTemplate;
import st.orm.template.QueryBuilder;
import st.orm.template.SqlTemplateException;
import st.orm.template.TableNameResolver;
import st.orm.template.TemplateFunction;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.spi.Providers.getORMConverter;
import static st.orm.template.impl.SqlTemplateImpl.getColumnName;
import static st.orm.template.impl.SqlTemplateImpl.getForeignKey;
import static st.orm.template.impl.SqlTemplateImpl.getLazyRecordType;
import static st.orm.template.impl.SqlTemplateImpl.isAutoGenerated;

class ORMTemplateImpl implements ORMTemplate {
    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    private final QueryFactory factory;
    protected final TableNameResolver tableNameResolver;
    protected final ColumnNameResolver columnNameResolver;
    protected final ForeignKeyResolver foreignKeyResolver;
    protected final Predicate<? super EntityRepositoryProvider> providerFilter;

    public ORMTemplateImpl(@Nonnull QueryFactory factory,
                           @Nullable TableNameResolver tableNameResolver,
                           @Nullable ColumnNameResolver columnNameResolver,
                           @Nullable ForeignKeyResolver foreignKeyResolver,
                           @Nullable Predicate<? super EntityRepositoryProvider> providerFilter) {
        this.factory = requireNonNull(factory);
        this.tableNameResolver = tableNameResolver;
        this.columnNameResolver = columnNameResolver;
        this.foreignKeyResolver = foreignKeyResolver;
        this.providerFilter = providerFilter;
    }

    @Override
    public BindVars createBindVars() {
        return factory.createBindVars();
    }

    @Override
    public <T extends Record & Entity<ID>, ID> EntityModel<T, ID> model(@Nonnull Class<T> type) {
        return createEntityModel(type);
    }

    @Override
    public <T extends Record> QueryBuilder<T, T, ?> selectFrom(@Nonnull Class<T> fromType) {
        return new QueryBuilderImpl<>(this, fromType, fromType);
    }

    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType, Class<R> selectType) {
        return new QueryBuilderImpl<>(this, fromType, selectType);
    }

    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType, Class<R> selectType, @Nonnull StringTemplate template) {
        return new QueryBuilderImpl<>(this, fromType, selectType, template);
    }

    @Override
    public <T extends Record, R> QueryBuilder<T, R, ?> selectFrom(@Nonnull Class<T> fromType, Class<R> selectType, @Nonnull TemplateFunction templateFunction) {
        return new QueryBuilderImpl<>(this, fromType, selectType, TemplateFunctionHelper.template(templateFunction));
    }

    @Override
    public Query query(@Nonnull StringTemplate template) {
        return factory.create(new LazyFactoryImpl(factory, tableNameResolver, columnNameResolver, foreignKeyResolver, providerFilter), template);
    }

    @Override
    public Query query(@Nonnull TemplateFunction function) {
        return query(TemplateFunctionHelper.template(function));
    }

    private <T extends Record & Entity<ID>, ID> EntityModel<T, ID> createEntityModel(@Nonnull Class<T> type) throws PersistenceException {
        if (!type.isRecord()) {
            throw new PersistenceException(STR."Entity type must be a record: \{type.getSimpleName()}.");
        }
        List<Column> columns = Stream.of(type.getRecordComponents())
                .flatMap(c -> {
                    boolean primaryKey = REFLECTION.isAnnotationPresent(c, PK.class);
                    boolean autoGenerated = primaryKey && isAutoGenerated(c);
                    return createColumns(c, primaryKey, autoGenerated).stream();
                })
                .toList();
        String tableName;
        Name name = REFLECTION.getAnnotation(type, Name.class);
        if (name != null) {
            tableName = name.value();
        } else if (tableNameResolver != null) {
            tableName = tableNameResolver.resolveTableName(type);
        } else {
            tableName = type.getSimpleName();
        }
        //noinspection unchecked
        Class<ID> pkType =  (Class<ID>) REFLECTION.findPKType(type)
                .orElseThrow(() -> new PersistenceException(STR."No primary key found for entity type: \{type.getSimpleName()}."));
        return new EntityModel<>(tableName, type, pkType, columns);
    }

    private List<Column> createColumns(@Nonnull RecordComponent component, boolean primaryKey, boolean autoGenerated) {
        try {
            var converter = getORMConverter(component).orElse(null);
            if (converter != null) {
                var columnTypes = converter.getParameterTypes();
                var columnNames = converter.getColumns(c -> getColumnName(c, columnNameResolver));
                if (columnTypes.size() != columnNames.size()) {
                    throw new SqlTemplateException("Column count does not match value count.");
                }
                var columns = new ArrayList<Column>(columnTypes.size());
                for (int i = 0; i < columnTypes.size(); i++) {
                    columns.add(new Column(columnNames.get(i), columnTypes.get(i), primaryKey,
                            false, false, true, true, true, false, false));
                }
                return columns;
            }
            Class<?> componentType = component.getType();
            boolean foreignKey = REFLECTION.isAnnotationPresent(component, FK.class);
            String columnName;
            if (foreignKey) {
                columnName = getForeignKey(component, foreignKeyResolver);
            } else {
                if (componentType.isRecord()) {
                    // @Inline is implicitly assumed.
                    if (REFLECTION.isAnnotationPresent(component, Name.class)) {
                        throw new SqlTemplateException("Name annotation is not allowed on @PK or @Inline records.");
                    }
                    return Stream.of(componentType.getRecordComponents())
                            .flatMap(c -> createColumns(c, primaryKey, autoGenerated).stream())
                            .toList();
                }
                columnName = getColumnName(component, columnNameResolver);
            }
            boolean nullable = !REFLECTION.isNonnull(component);
            Persist persist = REFLECTION.getAnnotation(component, Persist.class);
            boolean version = REFLECTION.isAnnotationPresent(component, Version.class);
            boolean insertable = persist == null || persist.insertable();
            boolean updatable = persist == null || persist.updatable();
            boolean lazy = Lazy.class.isAssignableFrom(componentType);
            if (lazy) {
                try {
                    componentType = getLazyRecordType(component);
                } catch (SqlTemplateException e) {
                    throw new PersistenceException(e);
                }
            }
            return List.of(new Column(columnName, componentType, primaryKey, autoGenerated, foreignKey, nullable, insertable, updatable, version, lazy));
        } catch (SqlTemplateException e) {
            throw new PersistenceException(e);
        }
    }
}
