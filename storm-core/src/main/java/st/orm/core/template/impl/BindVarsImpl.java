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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.core.template.SqlTemplate.PositionalParameter;
import st.orm.BindVars;
import st.orm.PersistenceException;
import st.orm.core.template.SqlTemplate.BatchListener;
import st.orm.core.template.SqlTemplate.BindVariables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.lang.Long.toHexString;
import static java.lang.System.identityHashCode;

/**
 * Manages the binding of variables in a SQL query.
 *
 * <p>Provides a handle for configuring parameter bindings and allows registering a listener for batch processing
 * events.</p>
 */
final class BindVarsImpl implements BindVars, BindVariables {
    private final List<Function<Record, List<PositionalParameter>>> parameterExtractors;
    private BatchListener batchListener;
    private RecordListener recordListener;

    public BindVarsImpl() {
        this.parameterExtractors = new ArrayList<>();
    }

    /**
     * Retrieves a handle to the bound variables. Can be used for configuring or inspecting the
     * parameters associated with the current query.
     *
     * @return a handle to the bound variables.
     */
    @Override
    public BindVarsHandle getHandle() {
        return record -> {
            if (batchListener == null) {
                throw new IllegalStateException("Batch listener not set.");
            }
            if (parameterExtractors.isEmpty()) {
                throw new IllegalStateException("No parameter extractors not set.");
            }
            if (recordListener != null) {
                recordListener.onRecord(record);
            }
            try {
                var positionalParameters = parameterExtractors.stream()
                        .flatMap(pe -> pe.apply(record).stream())
                        .toList();
                batchListener.onBatch(positionalParameters);
            } catch (UncheckedSqlTemplateException e) {
                throw new PersistenceException(e.getCause());
            }
        };
    }

    /**
     * Sets a listener that is invoked for each record that is being bound.
     *
     * <p>This method can be used to optimize the binding of parameters to the SQL statement.</p>
     *
     * @param listener the consumer to invoke for each bind variable.
     * @since 1.2
     */
    @Override
    public void setRecordListener(@Nonnull RecordListener listener) {
        if (recordListener != null) {
            throw new PersistenceException("Record listener already set.");
        }
        this.recordListener = listener;
    }

    /**
     * Registers a listener that will be notified when positional parameters are processed in batches.
     *
     * @param listener the listener to be notified of batch events.
     */
    @Override
    public void setBatchListener(@Nonnull BatchListener listener) {
        if (batchListener != null) {
            throw new PersistenceException("Record listener already set.");
        }
        this.batchListener = listener;
    }

    /**
     * Registers a function that extracts positional parameters from a record.
     *
     * <p><strong>Note:</strong> The {@code parameterExtractor} function may raise an {
     * @code UncheckedSqlTemplateException} if the record does not comply with the expected format or if there are
     * issues.</p>
     *
     * @param parameterExtractor the function that extracts positional parameters from a record.
     */
    void addParameterExtractor(@Nonnull Function<Record, List<PositionalParameter>> parameterExtractor) {
        parameterExtractors.add(parameterExtractor);
    }

    @Override
    public String toString() {
        return "%s@%s".formatted(getClass().getSimpleName(), toHexString(identityHashCode(this)));
    }
}
