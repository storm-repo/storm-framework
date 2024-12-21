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
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.from;

public class SelectBuilderImpl<T extends Record, R, ID> extends QueryBuilderImpl<T, R, ID> {
    private final StringTemplate selectTemplate;
    private final Class<R> selectType;
    private final boolean distinct;
    private final boolean subquery;

    public SelectBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                             @Nonnull Class<T> fromType,
                             @Nonnull Class<R> selectType,
                             @Nonnull StringTemplate selectTemplate,
                             boolean subquery) {
        this(queryTemplate, fromType, selectType, false, List.of(), List.of(), selectTemplate, List.of(), subquery);
    }

    private SelectBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                              @Nonnull Class<T> fromType,
                              @Nonnull Class<R> selectType,
                              boolean distinct,
                              @Nonnull List<Join> join,
                              @Nonnull List<Where> where,
                              @Nonnull StringTemplate selectTemplate,
                              @Nonnull List<StringTemplate> templates,
                              boolean subquery) {
        super(queryTemplate, fromType, join, where, templates);
        this.selectType = selectType;
        this.distinct = distinct;
        this.selectTemplate = selectTemplate;
        this.subquery = subquery;
    }

    @Override
    QueryBuilder<T, R, ID> copyWith(@Nonnull QueryTemplate orm,
                                    @Nonnull Class<T> fromType,
                                    @Nonnull List<Join> join,
                                    @Nonnull List<Where> where,
                                    @Nonnull List<StringTemplate> templates) {
        return new SelectBuilderImpl<>(orm, fromType, selectType, distinct, join, where, selectTemplate, templates, subquery);
    }

    @Override
    boolean supportsJoin() {
        return true;
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> distinct() {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, true, join, where, selectTemplate, templates, subquery);
    }

    private StringTemplate toStringTemplate() {
        StringTemplate template = StringTemplate.combine(StringTemplate.of(STR."SELECT \{distinct ? "DISTINCT " : ""}"), selectTemplate);
        template = StringTemplate.combine(template, RAW."\nFROM \{from(fromType, true)}");
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
        return template;
    }

    @Override
    public StringTemplate getStringTemplate() {
        return toStringTemplate();
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    @Override
    public Query build() {
        if (subquery) {
            throw new PersistenceException("Cannot build a query from a subquery.");
        }
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
        return build().getResultStream(selectType);
    }
}
