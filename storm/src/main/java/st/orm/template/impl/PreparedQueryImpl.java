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
import st.orm.PersistenceException;
import st.orm.PreparedQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Stream.generate;
import static st.orm.template.impl.ObjectMapperFactory.getObjectMapper;

/**
 *
 */
final class PreparedQueryImpl extends QueryImpl implements PreparedQuery {

    private final LazyFactory lazyFactory;
    private final PreparedStatement statement;
    private final BindVarsHandle bindVarsHandle;

    public PreparedQueryImpl(@Nonnull LazyFactory lazyFactory,
                             @Nonnull PreparedStatement statement,
                             @Nullable BindVarsHandle bindVarsHandle,
                             boolean versionAware) {
        super(lazyFactory, _ -> statement, bindVarsHandle, versionAware);
        this.lazyFactory = lazyFactory;
        this.statement = statement;
        this.bindVarsHandle = bindVarsHandle;
    }

    @Override
    protected boolean closeStatement() {
        return false;
    }

    /**
     * Add a record to the batch.
     *
     * @param record the record to add to the batch.
     * @throws PersistenceException if adding the batch fails, for instance when query has not specified
     * {@code BatchVars}.
     */
    @Override
    public void addBatch(@Nonnull Record record) {
        if (bindVarsHandle == null) {
            throw new PersistenceException("No bind vars specified for prepared query.");
        }
        bindVarsHandle.addBatch(record);
    }

    /**
     * Returns a stream of generated keys as the result of an insert statement. Returns an empty stream if the insert
     * statement did not generate any keys.
     *
     * <p>The returned stream allows for lazy processing, meaning elements are generated only as they are consumed,
     * optimizing resource usage. Note, however, that calling this method does trigger the execution of the underlying
     * query, so it should only be invoked when the query is intended to run. Since the stream holds resources open
     * while in use, it must be closed after usage to prevent resource leaks. As the stream is AutoCloseable, it is
     * recommended to use it within a try-with-resources block.</p>
     *
     * @param <ID> the type of generated keys
     * @param type the class of the keys
     * @return a stream of generated keys resulting from an insert statement; returns an empty stream if no keys are
     * generated.
     * @throws PersistenceException if the statement fails
     */    @Override
    public <ID> Stream<ID> getGeneratedKeys(@Nonnull Class<ID> type) {
        try {
            ResultSet resultSet = statement.getGeneratedKeys();
            boolean close = true;
            try {
                int columnCount = resultSet.getMetaData().getColumnCount();
                var mapper = getObjectMapper(columnCount, type, lazyFactory)
                        .orElseThrow(() -> new PersistenceException(STR."No suitable constructor found for \{type}."));
                close = false;
                return MonitoredResource.wrap(
                        generate(() -> readNext(resultSet, columnCount, mapper))
                                .takeWhile(Objects::nonNull)
                                .onClose(() -> close(resultSet, statement)));
            } finally {
                if (close) {
                    resultSet.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public void close() {
        try {
            statement.close();
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
    }
}
