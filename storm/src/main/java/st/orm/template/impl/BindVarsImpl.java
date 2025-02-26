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
import st.orm.BindVars;
import st.orm.PersistenceException;
import st.orm.template.SqlTemplate.BatchListener;
import st.orm.template.SqlTemplate.BindVariables;
import st.orm.template.SqlTemplate.PositionalParameter;
import st.orm.template.SqlTemplate.RecordListener;

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
            var positionalParameters = parameterExtractors.stream()
                    .flatMap(pe -> pe.apply(record).stream())
                    .toList();
            batchListener.onBatch(positionalParameters);
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

    void addParameterExtractor(@Nonnull Function<Record, List<PositionalParameter>> parameterExtractor) {
        parameterExtractors.add(parameterExtractor);
    }

    @Override
    public String toString() {
        return STR."\{getClass().getSimpleName()}@\{toHexString(identityHashCode(this))}";
    }
}
