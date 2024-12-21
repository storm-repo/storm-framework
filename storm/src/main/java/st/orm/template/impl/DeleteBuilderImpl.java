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
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.repository.Column;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.from;
import static st.orm.Templates.subquery;

public class DeleteBuilderImpl<T extends Record, R, ID> extends QueryBuilderImpl<T, R, ID> {

    public DeleteBuilderImpl(@Nonnull QueryTemplate orm, @Nonnull Class<T> fromType) {
        this(orm, fromType, List.of(), List.of(), List.of());
    }

    private DeleteBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                              @Nonnull Class<T> fromType,
                              @Nonnull List<Join> join,
                              @Nonnull List<Where> where,
                              @Nonnull List<StringTemplate> templates) {
        super(queryTemplate, fromType, join, where, templates);
    }

    @Override
    QueryBuilder<T, R, ID> copyWith(@Nonnull QueryTemplate queryTemplate,
                                    @Nonnull Class<T> fromType,
                                    @Nonnull List<Join> join,
                                    @Nonnull List<Where> where,
                                    @Nonnull List<StringTemplate> templates) {
        return new DeleteBuilderImpl<>(queryTemplate, fromType, join, where, templates);
    }

    /**
     * Indicates whether the DELETE query supports joins.
     */
    protected boolean supportsJoin() {
        return false;
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> distinct() {
        throw new PersistenceException("Cannot use DISTINCT in a DELETE query.");
    }

    private StringTemplate getPrimaryKeyTemplate(boolean alias) {
        var model = queryTemplate.model(fromType);
        return model.columns().stream()
                .filter(Column::primaryKey)
                .map(c -> {
                    if (alias) {
                        return StringTemplate.combine(RAW."\{fromType}.", StringTemplate.of(c.columnName()));
                    } else {
                        return StringTemplate.of(c.columnName());
                    }
                })
                .reduce((a, b) -> StringTemplate.combine(a, RAW.", ", b))
                .orElseThrow(() -> new PersistenceException(STR."No primary key found for table: \{fromType.getSimpleName()}."));
    }

    private StringTemplate toStringTemplate() {
        StringTemplate template;
        if (supportsJoin()) {
            template = RAW."DELETE \{fromType}\nFROM \{from(fromType, supportsJoin())}";
        } else {
            template = StringTemplate.combine(RAW."SELECT ", getPrimaryKeyTemplate(true), RAW."\nFROM \{from(fromType, true)}");
        }
        //noinspection DuplicatedCode
        if (!join.isEmpty()) {
            template = join.stream()
                    .reduce(template,
                            (acc, join) -> StringTemplate.combine(acc, RAW."\{join}"),
                            StringTemplate::combine);
        }
        if (!where.isEmpty()) {
            // Leave handling of multiple where's to the sql processor.
            template = where.stream()
                    .reduce(StringTemplate.combine(template, RAW."\nWHERE "),
                            (acc, where) -> StringTemplate.combine(acc, RAW."\{where}"),
                            StringTemplate::combine);
        }
        if (!templates.isEmpty()) {
            template = StringTemplate.combine(template, StringTemplate.combine(templates));
        }
        if (!supportsJoin()) {
            template = StringTemplate.combine(RAW."""
                DELETE
                FROM \{from(fromType, false)}
                WHERE (""", getPrimaryKeyTemplate(false), RAW.") IN (", RAW."\{subquery(template, false)}", RAW.")");
        }
        return template;
    }

    @Override
    public StringTemplate getStringTemplate() {
        throw new PersistenceException("Cannot use a DELETE query as subquery.");
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    @Override
    public Query build() {
        return queryTemplate.query(toStringTemplate());
    }

    /**
     * Executes the query and returns a stream of results.
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p>Note that calling this method does trigger the execution of the underlying query, so it should only be invoked
     * when the query is intended to run. Since the stream holds resources open while in use, it must be closed after
     * usage to prevent resource leaks. As the stream is AutoCloseable, it is recommended to use it within a
     * try-with-resources block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public Stream<R> getResultStream() {
        throw new PersistenceException("Cannot get a result stream from a DELETE query.");
    }
}
