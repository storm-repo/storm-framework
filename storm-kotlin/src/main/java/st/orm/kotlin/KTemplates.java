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
package st.orm.kotlin;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.TemporalType;
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.kotlin.template.KORMRepositoryTemplate;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.impl.KORMRepositoryTemplateImpl;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.ORMTemplate;
import st.orm.template.impl.Element;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.JpaTemplateImpl;
import st.orm.template.impl.PreparedStatementTemplateImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.spi.Providers.getORMReflection;
import static st.orm.template.impl.Elements.Alias;
import static st.orm.template.impl.Elements.Delete;
import static st.orm.template.impl.Elements.From;
import static st.orm.template.impl.Elements.Insert;
import static st.orm.template.impl.Elements.Param;
import static st.orm.template.impl.Elements.Select;
import static st.orm.template.impl.Elements.Set;
import static st.orm.template.impl.Elements.Table;
import static st.orm.template.impl.Elements.Unsafe;
import static st.orm.template.impl.Elements.Update;
import static st.orm.template.impl.Elements.Values;
import static st.orm.template.impl.Elements.Where;

/**
 * Base interface for templates.
 */
public interface KTemplates {
    static KORMTemplate ORM(@Nonnull ORMTemplate template) {
        return new KORMTemplateImpl(template);
    }

    static KORMRepositoryTemplate ORM(@Nonnull ORMRepositoryTemplate template) {
        return new KORMRepositoryTemplateImpl(template);
    }

    static KORMTemplate ORM(@Nonnull EntityManager entityManager) {
        return ORM(new JpaTemplateImpl(entityManager).toORM());
    }

    static KORMRepositoryTemplate ORM(@Nonnull DataSource dataSource) {
        return ORM(new PreparedStatementTemplateImpl(dataSource).toORM());
    }

    static KORMRepositoryTemplate ORM(@Nonnull Connection connection) {
        return ORM(new PreparedStatementTemplateImpl(connection).toORM());
    }

    static Element values(@Nonnull Stream<? extends Record> records) {
        return new Values(requireNonNull(records, "records"), null);
    }
    default Element v(@Nonnull Stream<? extends Record> records) {
        return values(records);
    }
    static Element values(@Nonnull Record record) {
        return new Values(Stream.of(record), null);
    }
    default Element v(@Nonnull Record record) {
        return values(record);
    }
    static Element values(@Nonnull BindVars bindVars) {
        return new Values(null, requireNonNull(bindVars, "bindVars"));
    }
    default Element v(@Nonnull BindVars bindVars) {
        return values(bindVars);
    }

    static Element set(@Nonnull Record record) {
        return new Set(requireNonNull(record, "record"), null);
    }
    default Element st(@Nonnull Record record) {
        return set(record);
    }
    static Element set(@Nonnull BindVars bindVars) {
        return new Set(null, requireNonNull(bindVars, "bindVars"));
    }
    default Element st(@Nonnull BindVars bindVars) {
        return set(bindVars);
    }

    static Element where(@Nonnull Expression expression) {
        return new Where(expression, null);
    }
    default Element w(@Nonnull Expression expression) {
        return where(expression);
    }
    static Element where(@Nonnull Object object) {
        return new Where(new ObjectExpression(object), null);
    }
    default Element w(@Nonnull Object object) {
        return where(object);
    }
    static Element where(@Nonnull BindVars bindVars) {
        return new Where(null, requireNonNull(bindVars, "bindVars"));
    }
    default Element w(@Nonnull BindVars bindVars) {
        return where(bindVars);
    }

    static Element param(@Nullable Object value) {
        return new Param(null, value);
    }
    default Element p(@Nullable Object value) {
        return param(value);
    }
    static Element param(@Nonnull String name, @Nullable Object value) {
        return new Param(requireNonNull(name, "name"), value);
    }
    default Element p(@Nonnull String name, @Nullable Object value) {
        return param(name, value);
    }
    static <P> Element param(@Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(null, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }
    default <P> Element p(@Nullable P value, @Nonnull Function<? super P, ?> converter) {
        return param(value, converter);
    }
    static <P> Element param(@Nonnull String name, @Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(name, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }
    default <P> Element p(@Nonnull String name, @Nullable P value, @Nonnull Function<? super P, ?> converter) {
        return param(name, value, converter);
    }
    static Element param(@Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }
    default Element p(@Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(value, temporalType);
    }
    static Element param(@Nonnull String name, @Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }
    default Element p(@Nonnull String name, @Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(name, value, temporalType);
    }
    static Element param(@Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }
    default Element p(@Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(value, temporalType);
    }
    static Element param(@Nonnull String name, @Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }
    default Element p(@Nonnull String name, @Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(name, value, temporalType);
    }

    static Element unsafe(@Nonnull String sql) {
        return new Unsafe(sql);
    }

    /**
     * Returns the single result of the stream.
     *
     * @param stream the stream to get the single result from.
     * @return the single result of the stream.
     * @param <T> the type of the result.
     * @throws NoResultException if there is no result.
     * @throws NonUniqueResultException if more than one result.
     */
    default <T> T singleResult(Stream<T> stream) {
        return stream
                .reduce((_, _) -> {
                    throw new NonUniqueResultException("Expected single result, but found more than one.");
                }).orElseThrow(() -> new NoResultException("Expected single result, but found none."));
    }

    /**
     * Create a new bind variables instance that can be used to add bind variables to a batch.
     *
     * @return a new bind variables instance.
     * @see BindVars#addBatch(Record)
     */
    BindVars createBindVars();

    static Element select(KClass<? extends Record> table) {
        return new Select(getORMReflection().getRecordType(table));
    }
    default Element s(KClass<? extends Record> table) {
        return select(table);
    }

    static Element insert(@Nonnull KClass<? extends Record> table) {
        return new Insert(getORMReflection().getRecordType(table));
    }
    default Element i(@Nonnull KClass<? extends Record> table) {
        return insert(table);
    }

    static Element update(@Nonnull KClass<? extends Record> table) {
        return new Update(getORMReflection().getRecordType(table));
    }
    default Element u(@Nonnull KClass<? extends Record> table) {
        return update(table);
    }
    static Element update(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Update(getORMReflection().getRecordType(table), alias);
    }
    default Element u(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return update(table, alias);
    }

    static Element delete(@Nonnull KClass<? extends Record> table) {
        return new Delete(getORMReflection().getRecordType(table));
    }
    default Element d(@Nonnull KClass<? extends Record> table) {
        return delete(table);
    }
    static Element delete(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Delete(getORMReflection().getRecordType(table), alias);
    }
    default Element d(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return delete(table, alias);
    }

    static Element from(@Nonnull KClass<? extends Record> table) {
        return new From(getORMReflection().getRecordType(table));
    }
    default Element f(@Nonnull KClass<? extends Record> table) {
        return from(table);
    }
    static Element from(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new From(new TableSource(getORMReflection().getRecordType(table)), requireNonNull(alias, "alias"));
    }
    default Element f(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return from(table, alias);
    }

    static Element table(@Nonnull KClass<? extends Record> table) {
        return new Table(getORMReflection().getRecordType(table));
    }
    default Element t(@Nonnull KClass<? extends Record> table) {
        return table(table);
    }
    static Element table(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Table(getORMReflection().getRecordType(table), alias);
    }
    default Element t(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return table(table, alias);
    }

    static Element alias(@Nonnull KClass<? extends Record> table) {
        return new Alias(getORMReflection().getRecordType(table));
    }
    default Element a(@Nonnull KClass<? extends Record> table) {
        return alias(table);
    }    
}
