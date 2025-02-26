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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import st.orm.BindVars;
import st.orm.PreparedQuery;
import st.orm.Query;
import st.orm.spi.QueryFactory;
import st.orm.template.JpaTemplate;
import st.orm.template.ORMTemplate;
import st.orm.template.SqlTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static jakarta.persistence.TemporalType.DATE;
import static jakarta.persistence.TemporalType.TIME;
import static jakarta.persistence.TemporalType.TIMESTAMP;
import static st.orm.template.SqlTemplate.JPA;

public final class JpaTemplateImpl implements JpaTemplate {

    @FunctionalInterface
    private interface TemplateProcessor {
        jakarta.persistence.Query process(@Nonnull StringTemplate stringTemplate, @Nullable Class<?> resultClass, boolean safe);
    }

    private final TemplateProcessor templateProcessor;

    public JpaTemplateImpl(@Nonnull EntityManager entityManager) {
        templateProcessor = (template, resultClass, safe) -> {
            try {
                var sql = JPA.process(template);
                if (!safe) {
                    sql.unsafeWarning().ifPresent(warning -> {
                        throw new st.orm.PersistenceException(STR."\{warning} Use Query.safe() to mark query as safe.");
                    });
                }
                //noinspection SqlSourceToSinkFlow
                jakarta.persistence.Query query = resultClass == null
                        ? entityManager.createNativeQuery(sql.statement())
                        : entityManager.createNativeQuery(sql.statement(), resultClass);
                setParameters(query, sql.parameters());
                return query;
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
        };
    }

    private void setParameters(@Nonnull jakarta.persistence.Query query, @Nonnull List<SqlTemplate.Parameter> parameters) {
        for (var parameter : parameters) {
            var dbValue = parameter.dbValue();
            switch (parameter) {
                case SqlTemplate.PositionalParameter p -> {
                    switch (dbValue) {
                        case null -> query.setParameter(p.position(), null);
                        case java.sql.Date d -> query.setParameter(p.position(), d, DATE);
                        case java.sql.Time d -> query.setParameter(p.position(), d, TIME);
                        case java.sql.Timestamp d -> query.setParameter(p.position(), d, TIMESTAMP);
                        default -> query.setParameter(p.position(), dbValue);
                    }
                }
                case SqlTemplate.NamedParameter n -> {
                    switch (dbValue) {
                        case null -> query.setParameter(n.name(), null);
                        case java.sql.Date d -> query.setParameter(n.name(), d, DATE);
                        case java.sql.Time d -> query.setParameter(n.name(), d, TIME);
                        case java.sql.Timestamp d -> query.setParameter(n.name(), d, TIMESTAMP);
                        default -> query.setParameter(n.name(), dbValue);
                    }
                }
            }
        }
    }

    /**
     * Creates a query for the specified query {@code template}.
     *
     * @param template the query template.
     * @return the query.
     */
    @Override
    public jakarta.persistence.Query query(@Nonnull StringTemplate template) {
        return templateProcessor.process(template, null, true); // We allow unsafe queries in direct JPA mode.
    }

    private jakarta.persistence.Query query(@Nonnull StringTemplate template, @Nonnull Class<?> resultClass) {
        return templateProcessor.process(template, resultClass, true);  // We allow unsafe queries in direct JPA mode.
    }

    /**
     * Returns an ORM template for this JPA template.
     */
    @Override
    public ORMTemplate toORM() {
        return new ORMTemplateImpl(new QueryFactory() {
            @Override
            public BindVars createBindVars() {
                throw new PersistenceException("Not supported by JPA.");
            }

            @Override
            public Query create(@Nonnull StringTemplate template) {
                return new JpaPreparedQuery(template, false);
            }
        }, ModelBuilder.newInstance(), null);
    }

    private class JpaPreparedQuery implements PreparedQuery {
        private final StringTemplate template;
        private final boolean safe;

        public JpaPreparedQuery(StringTemplate template, boolean safe) {
            this.template = template;
            this.safe = safe;
        }

        @Override
        public PreparedQuery prepare() {
            return this;
        }

        @Override
        public Query safe() {
            return new JpaPreparedQuery(template, safe);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Stream<Object[]> getResultStream() {
            return JpaTemplateImpl.this.query(template).getResultStream().map(this::convert);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Stream<T> getResultStream(@Nonnull Class<T> type) {
            return query(template, type).getResultStream();
        }

        @Override
        public boolean isVersionAware() {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public int executeUpdate() {
            return JpaTemplateImpl.this.query(template).executeUpdate();
        }

        /**
         * Converts a database row into a list of values.
         *
         * @param row row to convert.
         * @return an array of values.
         */
        private Object[] convert(@Nullable Object row) {
            if (row == null || !row.getClass().isArray()) {
                return new Object[]{row};
            }
            return (Object[]) row;
        }

        @Override
        public void addBatch(@Nonnull Record record) {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public int[] executeBatch() {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public <ID> Stream<ID> getGeneratedKeys(@Nonnull Class<ID> type) {
            throw new UnsupportedOperationException("Not supported by JPA.");
        }

        @Override
        public void close() {
        }
    }
}
