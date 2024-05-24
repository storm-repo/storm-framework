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
import jakarta.persistence.PersistenceException;
import st.orm.PreparedQuery;

import java.sql.PreparedStatement;
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
        super(lazyFactory, () -> statement, bindVarsHandle, versionAware);
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
     * Returns the generated keys as result of an insert statement. Returns an empty stream if the statement did not
     * generate any keys.
     *
     * @return the generated keys as result of an insert statement.
     * @throws PersistenceException if the statement fails.
     */
    @Override
    public <ID> Stream<ID> getGeneratedKeys(@Nonnull Class<ID> type) {
        try {
            boolean close = true;
            var resultSet = statement.getGeneratedKeys();
            try {
                int columnCount = resultSet.getMetaData().getColumnCount();
                if (columnCount == 0) {
                    throw new PersistenceException("No generated keys available. Support varies across databases, often limited to auto-increment columns.");
                }
                var mapper = getObjectMapper(columnCount, type, lazyFactory)
                        .orElseThrow(() -> new PersistenceException(STR."No suitable constructor found for \{type}."));
                // The result set will be closed when the stream is closed, but also when readNext returns null.
                var stream = generate(() -> readNext(resultSet, columnCount, mapper))
                        .takeWhile(Objects::nonNull)
                        .onClose(() -> {
                            try {
                                resultSet.close();
                            } catch (SQLException e) {
                                throw new PersistenceException(e);
                            }
                        });
                close = false;
                return AutoClosingStreamProxy.wrap(stream);
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
