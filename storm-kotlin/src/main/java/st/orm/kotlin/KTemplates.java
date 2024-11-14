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
import kotlin.reflect.KClass;
import st.orm.BindVars;
import st.orm.TemporalType;
import st.orm.kotlin.template.KORMRepositoryTemplate;
import st.orm.kotlin.template.KORMTemplate;
import st.orm.kotlin.template.impl.KORMRepositoryTemplateImpl;
import st.orm.kotlin.template.impl.KORMTemplateImpl;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
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
import static st.orm.template.Operator.EQUALS;
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
    static Element values(@Nonnull Record record) {
        return new Values(Stream.of(record), null);
    }
    static Element values(@Nonnull BindVars bindVars) {
        return new Values(null, requireNonNull(bindVars, "bindVars"));
    }

    static Element set(@Nonnull Record record) {
        return new Set(requireNonNull(record, "record"), null);
    }
    static Element set(@Nonnull BindVars bindVars) {
        return new Set(null, requireNonNull(bindVars, "bindVars"));
    }

    static Element where(@Nonnull Expression expression) {
        return new Where(expression, null);
    }
    static Element where(@Nonnull Iterable<?> it) {
        return new Where(new ObjectExpression(it, EQUALS, null), null);
    }
    static Element where(@Nonnull Object... o) {
        return new Where(new ObjectExpression(o, EQUALS, null), null);
    }
    static Element where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Iterable<?> it) {
        return new Where(new ObjectExpression(it, operator, path), null);
    }
    static Element where(@Nonnull String path, @Nonnull Operator operator, @Nonnull Object... o) {
        return new Where(new ObjectExpression(o, operator, path), null);
    }
    static Element where(@Nonnull BindVars bindVars) {
        return new Where(null, requireNonNull(bindVars, "bindVars"));
    }

    static Element param(@Nullable Object value) {
        return new Param(null, value);
    }
    static Element param(@Nonnull String name, @Nullable Object value) {
        return new Param(requireNonNull(name, "name"), value);
    }
    static <P> Element param(@Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(null, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }
    static <P> Element param(@Nonnull String name, @Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Param(name, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }
    static Element param(@Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }
    static Element param(@Nonnull String name, @Nonnull Date value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTime());
            case TIME -> new java.sql.Time(v.getTime());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTime());
        });
    }
    static Element param(@Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }
    static Element param(@Nonnull String name, @Nonnull Calendar value, @Nonnull TemporalType temporalType) {
        return param(name, value, v -> switch (temporalType) {
            case DATE -> new java.sql.Date(v.getTimeInMillis());
            case TIME -> new java.sql.Time(v.getTimeInMillis());
            case TIMESTAMP -> new java.sql.Timestamp(v.getTimeInMillis());
        });
    }

    static Element unsafe(@Nonnull String sql) {
        return new Unsafe(sql);
    }

    static Element select(KClass<? extends Record> table) {
        return new Select(getORMReflection().getRecordType(table));
    }

    static Element insert(@Nonnull KClass<? extends Record> table) {
        return new Insert(getORMReflection().getRecordType(table));
    }

    static Element update(@Nonnull KClass<? extends Record> table) {
        return new Update(getORMReflection().getRecordType(table));
    }
    static Element update(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Update(getORMReflection().getRecordType(table), alias);
    }

    static Element delete(@Nonnull KClass<? extends Record> table) {
        return new Delete(getORMReflection().getRecordType(table));
    }
    static Element delete(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Delete(getORMReflection().getRecordType(table), alias);
    }

    static Element from(@Nonnull KClass<? extends Record> table, boolean autoJoin) {
        return new From(getORMReflection().getRecordType(table), autoJoin);
    }
    static Element from(@Nonnull KClass<? extends Record> table, @Nonnull String alias, boolean autoJoin) {
        return new From(new TableSource(getORMReflection().getRecordType(table)), requireNonNull(alias, "alias"), autoJoin);
    }

    static Element table(@Nonnull KClass<? extends Record> table) {
        return new Table(getORMReflection().getRecordType(table));
    }
    static Element table(@Nonnull KClass<? extends Record> table, @Nonnull String alias) {
        return new Table(getORMReflection().getRecordType(table), alias);
    }

    static Element alias(@Nonnull KClass<? extends Record> table) {
        return alias(table, null);
    }
    static Element alias(@Nonnull KClass<? extends Record> table, @Nullable String path) {
        return new Alias(getORMReflection().getRecordType(table), path);
    }
}
