/*
 * Copyright 2024 - 2026 the original author or authors.
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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.Data;
import st.orm.PersistenceException;
import st.orm.core.template.Query;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.core.template.QueryBuilder;
import st.orm.core.template.QueryTemplate;
import st.orm.core.template.TemplateString;
import st.orm.core.template.impl.Elements.Where;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static st.orm.core.template.Templates.from;
import static st.orm.core.template.Templates.subquery;
import static st.orm.core.template.TemplateString.wrap;

/**
 * A query builder for DELETE queries.
 *
 * @param <T> the type of the table being queried.
 * @param <ID> the type of the primary key.
 */
public class DeleteBuilderImpl<T extends Data, ID> extends QueryBuilderImpl<T, Object, ID> {

    private final boolean safe;

    public DeleteBuilderImpl(@Nonnull QueryTemplate queryTemplate, @Nonnull Class<T> fromType, @Nonnull Supplier<Model<T, ID>> modelSupplier) {
        this(queryTemplate, fromType, List.of(), List.of(), List.of(), modelSupplier, false);
    }

    private DeleteBuilderImpl(@Nonnull QueryTemplate queryTemplate,
                              @Nonnull Class<T> fromType,
                              @Nonnull List<Join> join,
                              @Nonnull List<Where> where,
                              @Nonnull List<TemplateString> templates,
                              @Nonnull Supplier<Model<T, ID>> modelSupplier,
                              boolean safe) {
        super(queryTemplate, fromType, join, where, templates, modelSupplier);
        this.safe = safe;
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
    public QueryBuilder<T, Object, ID> safe() {
        return new DeleteBuilderImpl<>(queryTemplate, fromType, join, where, templates, modelSupplier, true);
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
    QueryBuilder<T, Object, ID> copyWith(@Nonnull QueryTemplate queryTemplate,
                                         @Nonnull Class<T> fromType,
                                         @Nonnull List<Join> join,
                                         @Nonnull List<Where> where,
                                         @Nonnull List<TemplateString> templates) {
        return new DeleteBuilderImpl<>(queryTemplate, fromType, join, where, templates, modelSupplier, safe);
    }

    /**
     * Indicates whether the DELETE query supports joins.
     */
    protected boolean supportsJoin() {
        // Only use joins if DELETE alias is supported by the SQL dialect.
        return queryTemplate.dialect().supportsDeleteAlias();
    }

    /**
     * Marks the current query as a distinct query.
     *
     * @return the query builder.
     */
    @Override
    public QueryBuilder<T, Object, ID> distinct() {
        throw new PersistenceException("Cannot use DISTINCT in a DELETE query.");
    }

    /**
     * Adds an OFFSET clause to the query.
     *
     * @param offset the offset.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, Object, ID> offset(int offset) {
        throw new PersistenceException("Cannot use OFFSET in a DELETE query.");
    }

    /**
     * Adds a LIMIT clause to the query.
     *
     * @param limit the maximum number of records to return.
     * @return the query builder.
     * @since 1.2
     */
    @Override
    public QueryBuilder<T, Object, ID> limit(int limit) {
        throw new PersistenceException("Cannot use LIMIT in a DELETE query.");
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     */
    @Override
    public QueryBuilder<T, Object, ID> forShare() {
        throw new PersistenceException("Cannot use FOR SHARE in a DELETE query.");
    }

    /**
     * Locks the selected rows for reading.
     *
     * @return the query builder.
     * @throws PersistenceException if the database does not support the specified lock mode, or if the lock mode is
     * not supported for the current query.
     */
    @Override
    public QueryBuilder<T, Object, ID> forUpdate() {
        throw new PersistenceException("Cannot use FOR UPDATE in a DELETE query.");
    }

    /**
     * Locks the selected rows using a custom lock mode.
     *
     * <p><strong>Note:</strong> This method results in non-portable code, as the lock mode is specific to the
     * underlying database.</p>
     *
     * @return the query builder.
     * @throws PersistenceException if the lock mode is not supported for the current query.
     */
    @Override
    public QueryBuilder<T, Object, ID> forLock(@Nonnull TemplateString template) {
        throw new PersistenceException("Cannot use FOR LOCK in a DELETE query.");
    }

    private TemplateString getPrimaryKeyTemplate(boolean alias) {
        var model = modelSupplier.get();
        return model.columns().stream()
                .filter(Column::primaryKey)
                .map(c -> {
                    if (alias) {
                        return TemplateString.combine(wrap(fromType), TemplateString.of("."), TemplateString.of(c.qualifiedName(queryTemplate.dialect())));
                    }
                    return TemplateString.of(c.qualifiedName(queryTemplate.dialect()));
                })
                .reduce((a, b) -> TemplateString.combine(a, TemplateString.of(", "), b))
                .orElseThrow(() -> new PersistenceException("No primary key found for table: %s.".formatted(fromType.getSimpleName())));
    }

    private TemplateString toTemplateString() {
        TemplateString template;
        if (supportsJoin()) {
            template = TemplateString.raw("DELETE \0\nFROM \0", fromType, from(fromType, supportsJoin()));
        } else {
            template = TemplateString.combine(TemplateString.of("SELECT "), getPrimaryKeyTemplate(true), TemplateString.raw("\nFROM \0", from(fromType, true)));
        }
        //noinspection DuplicatedCode
        if (!join.isEmpty()) {
            template = join.stream()
                    .reduce(template,
                            (acc, join) -> TemplateString.combine(acc, wrap(join)),
                            TemplateString::combine);
        }
        if (!where.isEmpty()) {
            // Leave handling of multiple where's to the sql processor.
            template = where.stream()
                    .reduce(TemplateString.combine(template, TemplateString.of("\nWHERE ")),
                            (acc, where) -> TemplateString.combine(acc, wrap(where)),
                            TemplateString::combine);
        }
        if (!templates.isEmpty()) {
            template = TemplateString.combine(template, TemplateString.combine(templates));
        }
        if (!supportsJoin()) {
            template = TemplateString.combine(TemplateString.raw("DELETE\nFROM \0\nWHERE (", from(fromType, false)),
                    getPrimaryKeyTemplate(false), TemplateString.of(") IN ("), wrap(subquery(template, false)), TemplateString.of(")"));
        }
        return template;
    }

    @Override
    public TemplateString getSubquery() {
        throw new PersistenceException("Cannot use a DELETE query as subquery.");
    }

    /**
     * Builds the query based on the current state of the query builder.
     *
     * @return the constructed query.
     */
    @Override
    public Query build() {
        Query query = queryTemplate.query(toTemplateString());
        if (safe || !where.isEmpty()) {
            query = query.safe();
        }
        return query;
    }

    /**
     * Executes the query and returns a stream of results.
     *
     * <p>The resulting stream is lazily loaded, meaning that the records are only retrieved from the database as they
     * are consumed by the stream. This approach is efficient and minimizes the memory footprint, especially when
     * dealing with large volumes of records.</p>
     *
     * <p><strong>Note:</strong> Calling this method does trigger the execution of the underlying query, so it should
     * only be invoked when the query is intended to run. Since the stream holds resources open while in use, it must be
     * closed after usage to prevent resource leaks. As the stream is {@code AutoCloseable}, it is recommended to use it
     * within a {@code try-with-resources} block.</p>
     *
     * @return a stream of results.
     * @throws PersistenceException if the query operation fails due to underlying database issues, such as
     *                              connectivity.
     */
    @Override
    public Stream<Object> getResultStream() {
        throw new PersistenceException("Cannot get a result stream from a DELETE query.");
    }
}
