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
        jakarta.persistence.Query process(@Nonnull StringTemplate stringTemplate, @Nullable Class<?> resultClass);
    }

    private final TemplateProcessor templateProcessor;

    public JpaTemplateImpl(@Nonnull EntityManager entityManager) {
        templateProcessor = (template, resultClass) -> {
            try {
                var sql = JPA.process(template);
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

    @Override
    public jakarta.persistence.Query process(StringTemplate template) {
        return templateProcessor.process(template, null);
    }

    private jakarta.persistence.Query process(@Nonnull StringTemplate template, @Nonnull Class<?> resultClass) {
        return templateProcessor.process(template, resultClass);
    }

    @Override
    public BindVars createBindVars() {
        throw new UnsupportedOperationException("Not supported by JPA.");
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

    @Override
    public ORMTemplate toORM() {
        return new ORMTemplateImpl(new QueryFactory() {
            @Override
            public BindVars createBindVars() {
                throw new PersistenceException("Not supported by JPA.");
            }

            @Override
            public Query create(@Nonnull LazyFactory lazyFactory, @Nonnull StringTemplate template) {
                return new PreparedQuery() {
                    @Override
                    public PreparedQuery prepare() {
                        return this;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public Stream<Object[]> getResultStream() {
                        return process(template).getResultStream().map(this::convert);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public <T> Stream<T> getResultStream(@Nonnull Class<T> type) {
                        return process(template, type).getResultStream();
                    }

                    @Override
                    public boolean isVersionAware() {
                        throw new UnsupportedOperationException("Not supported by JPA.");
                    }

                    @Override
                    public int executeUpdate() {
                        return process(template).executeUpdate();
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
                };
            }
        }, null, null, null, null);
    }
}
