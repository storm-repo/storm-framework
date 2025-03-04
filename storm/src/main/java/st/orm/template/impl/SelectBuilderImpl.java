/*
 * Copyright 2024 - 2025 the original author or authors.
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
import st.orm.PersistenceException;
import st.orm.Query;
import st.orm.template.Model;
import st.orm.template.QueryBuilder;
import st.orm.template.QueryTemplate;
import st.orm.template.impl.Elements.Where;
import st.orm.template.impl.SqlTemplateImpl.Join;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static st.orm.Templates.from;

/**
 * A query builder for SELECT queries.
 *
 * @param <T> the type of the table being queried.
 * @param <R> the type of the result.
 * @param <ID> the type of the primary key.
 */
public class SelectBuilderImpl<T extends Record, R, ID> extends QueryBuilderImpl<T, R, ID> {
    private final StringTemplate forLock;
    private final StringTemplate selectTemplate;
    private final Class<R> selectType;
    private final boolean distinct;
    private final Integer limit;
    private final Integer offset;
    private final boolean subquery;

    public SelectBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                             @Nonnull Class<T> fromType,
                             @Nonnull Class<R> selectType,
                             @Nonnull StringTemplate selectTemplate,
                             boolean subquery,
                             @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        this(queryTemplate, fromType, selectType, false, List.of(), List.of(), null, null, RAW."", selectTemplate, List.of(), subquery, modelSupplier);
    }

    private SelectBuilderImpl(@Nonnull QueryTemplate ormTemplate,
                              @Nonnull Class<T> fromType,
                              @Nonnull Class<R> selectType,
                              boolean distinct,
                              @Nonnull List<Join> join,
                              @Nonnull List<Where> where,
                              @Nullable Integer limit,
                              @Nullable Integer offset,
                              @Nonnull StringTemplate forLock,
                              @Nonnull StringTemplate selectTemplate,
                              @Nonnull List<StringTemplate> templates,
                              boolean subquery,
                              @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        super(ormTemplate, fromType, join, where, templates, modelSupplier);
        this.forLock = forLock;
        this.selectType = selectType;
        this.distinct = distinct;
        this.selectTemplate = selectTemplate;
        this.limit = limit;
        this.offset = offset;
        this.subquery = subquery;
    }

    /**
     * Returns a query builder that does not require a WHERE clause for UPDATE and DELETE queries.
     *
     * <p>This method is used to prevent accidental updates or deletions of all records in a table when a WHERE clause
     * is not provided.</p>
     *
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> safe() {
        return this;
    }

    /**
     * Returns a new query builder instance with the specified parameters.
     *
     * @param queryTemplate the query template.
     * @param fromType the type of the table being queried.
     * @param join the list of joins.
     * @param where the list of where clauses.
     * @param templates the list of string templates.
     * @return a new query builder.
     */
    @Override
    QueryBuilder<T, R, ID> copyWith(@Nonnull QueryTemplate queryTemplate,
                                    @Nonnull Class<T> fromType,
                                    @Nonnull List<Join> join,
                                    @Nonnull List<Where> where,
                                    @Nonnull List<StringTemplate> templates) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, distinct, join, where, limit, offset, forLock, selectTemplate, templates, subquery, modelSupplier);
    }

    /**
     * Returns true to indicate that the query supports joins, false otherwise.
     *
     * @return true if the query supports joins, false otherwise.
     */
    @Override
    protected boolean supportsJoin() {
        return true;
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, R, ID> distinct() {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, true, join, where, limit, offset, forLock, selectTemplate, templates, subquery, modelSupplier);
    }

    private StringTemplate toStringTemplate() {
        StringTemplate template = StringTemplate.combine(StringTemplate.of(STR."SELECT \{distinct ? "DISTINCT " : ""}"));
        if (queryTemplate.dialect().applyLimitAfterSelect()) {
            if (limit != null && offset == null) {
                template = StringTemplate.combine(template, StringTemplate.of(queryTemplate.dialect().limit(limit)), RAW." ");
            }
        }
        template = StringTemplate.combine(template, selectTemplate, RAW."\nFROM \{from(fromType, true)}");
        boolean hasLock = forLock.fragments().size() == 1 && !forLock.fragments().getFirst().isEmpty();
        if (hasLock && queryTemplate.dialect().applyLockHintAfterFrom()) {
            template = StringTemplate.combine(template, RAW."\n", forLock);
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
        if (!queryTemplate.dialect().applyLimitAfterSelect()) {
            if (limit != null && offset == null) {
                template = StringTemplate.combine(template, RAW."\n", StringTemplate.of(queryTemplate.dialect().limit(limit)));
            }
        }
        if (limit != null && offset != null) {
            template = StringTemplate.combine(template, RAW."\n", StringTemplate.of(queryTemplate.dialect().limit(offset, limit)));
        } else if (offset != null) {
            template = StringTemplate.combine(template, RAW."\n", StringTemplate.of(queryTemplate.dialect().offset(offset)));
        }
        if (hasLock && !queryTemplate.dialect().applyLockHintAfterFrom()) {
            template = StringTemplate.combine(template, RAW."\n", forLock);
        }
        return template;
    }

    @Override
    public StringTemplate getSubquery() {
        return toStringTemplate();
    }

    /**
     * Adds an OFFSET clause to the query.
     *
     * @param offset the offset.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> offset(int offset) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, distinct, join, where, limit, offset, forLock, selectTemplate, templates, subquery, modelSupplier);
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> limit(int limit) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, distinct, join, where, limit, offset, forLock, selectTemplate, templates, subquery, modelSupplier);
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> forShare() {
        return forLock(StringTemplate.of(queryTemplate.dialect().forShareLockHint()));
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> forUpdate() {
        return forLock(StringTemplate.of(queryTemplate.dialect().forUpdateLockHint()));
    }

    /**
     * Locks the selected rows using a custom lock mode.
     *
     * <p>Note that this method results in non-portable code, as the lock mode is specific to the underlying database.</p>
     *
     * @return the query builder.
     * @throws PersistenceException if the lock mode is not supported for the current query.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, R, ID> forLock(@Nonnull StringTemplate template) {
        return new SelectBuilderImpl<>(queryTemplate, fromType, selectType, distinct, join, where, limit, offset, template, selectTemplate, templates, subquery, modelSupplier);
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
