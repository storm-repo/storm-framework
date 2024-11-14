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
package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import st.orm.template.ORMRepositoryTemplate;
import st.orm.template.ORMTemplate;
import st.orm.template.Operator;
import st.orm.template.impl.Element;
import st.orm.template.impl.Elements;
import st.orm.template.impl.Elements.Expression;
import st.orm.template.impl.Elements.From;
import st.orm.template.impl.Elements.Insert;
import st.orm.template.impl.Elements.ObjectExpression;
import st.orm.template.impl.Elements.Set;
import st.orm.template.impl.Elements.TableSource;
import st.orm.template.impl.Elements.TemplateSource;
import st.orm.template.impl.Elements.Update;
import st.orm.template.impl.Elements.Values;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.JpaTemplateImpl;
import st.orm.template.impl.PreparedStatementTemplateImpl;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static st.orm.template.Operator.EQUALS;

/**
 * Base interface for templates.
 */
public interface Templates {

    /**
     * Returns an {@link ORMTemplate} for use with JPA.
     *
     * @param entityManager the entity manager to use.
     * @return an {@link ORMTemplate} for use with JPA.
     */
    static ORMTemplate ORM(@Nonnull EntityManager entityManager) {
        return new JpaTemplateImpl(entityManager).toORM();
    }

    /**
     * Returns an {@link ORMRepositoryTemplate} for use with JDBC.
     *
     * @param dataSource the data source to use.
     * @return an {@link ORMRepositoryTemplate} for use with JDBC.
     */
    static ORMRepositoryTemplate ORM(@Nonnull DataSource dataSource) {
        return new PreparedStatementTemplateImpl(dataSource).toORM();
    }

    /**
     * Returns an {@link ORMRepositoryTemplate} for use with JDBC.
     *
     * <p>The caller is responsible for closing the connection after usage.</p>
     *
     * @param connection the connection to use.
     * @return an {@link ORMRepositoryTemplate} for use with JDBC.
     */
    static ORMRepositoryTemplate ORM(@Nonnull Connection connection) {
        return new PreparedStatementTemplateImpl(connection).toORM();
    }

    static Element select(Class<? extends Record> table) {
        return new Elements.Select(table);
    }

    static Element insert(@Nonnull Class<? extends Record> table) {
        return new Insert(table);
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

    static Element update(@Nonnull Class<? extends Record> table) {
        return new Update(table);
    }
    static Element update(@Nonnull Class<? extends Record> table, @Nonnull String alias) {
        return new Update(table, alias);
    }

    static Element set(@Nonnull Record record) {
        return new Set(requireNonNull(record, "record"), null);
    }
    static Element set(@Nonnull BindVars bindVars) {
        return new Set(null, requireNonNull(bindVars, "bindVars"));
    }

    static Element where(@Nonnull Expression expression) {
        return new Where(requireNonNull(expression, "expression"), null);
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
        return new Where( null, requireNonNull(bindVars, "bindVars"));
    }

    static Element delete(@Nonnull Class<? extends Record> table) {
        return new Elements.Delete(table);
    }
    static Element delete(@Nonnull Class<? extends Record> table, @Nonnull String alias) {
        return new Elements.Delete(table, alias);
    }

    static Element from(@Nonnull Class<? extends Record> table, boolean autoJoin) {
        return new From(table, autoJoin);
    }
    static Element from(@Nonnull Class<? extends Record> table, @Nonnull String alias, boolean autoJoin) {
        return new From(new TableSource(table), requireNonNull(alias, "alias"), autoJoin);
    }
    static Element from(@Nonnull StringTemplate template, @Nonnull String alias) {
        return new From(new TemplateSource(template), requireNonNull(alias, "alias"), false);
    }

    static Element table(@Nonnull Class<? extends Record> table) {
        return new Elements.Table(table);
    }
    static Element table(@Nonnull Class<? extends Record> table, @Nonnull String alias) {
        return new Elements.Table(table, alias);
    }

    static Element alias(@Nonnull Class<? extends Record> table) {
        return alias(table, null);
    }
    static Element alias(@Nonnull Class<? extends Record> table, @Nullable String path) {
        return new Elements.Alias(table, path);
    }

    static Element param(@Nullable Object value) {
        return new Elements.Param(null, value);
    }
    static Element param(@Nonnull String name, @Nullable Object value) {
        return new Elements.Param(requireNonNull(name, "name"), value);
    }
    static <P> Element param(@Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Elements.Param(null, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
    }
    static <P> Element param(@Nonnull String name, @Nullable P value, @Nonnull Function<? super P, ?> converter) {
        //noinspection unchecked
        return new Elements.Param(name, value, (Function<Object, ?>) requireNonNull(converter, "converter"));
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
        return new Elements.Unsafe(sql);
    }
}
