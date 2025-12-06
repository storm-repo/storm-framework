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
package st.orm;

import jakarta.annotation.Nonnull;

/**
 * Marker interface used within an SQL template to indicate where bind variables (parameters) should be injected.
 *
 * <p>This interface is typically referenced within the SQL string to specify placeholder positions for parameter
 * binding.</p>
 */
public interface BindVars {

    /**
     * A listener for record events within a SQL query context.
     */
    @FunctionalInterface
    interface RecordListener {

        /**
         * Invoked when a new record is processed.
         *
         * @param record the record that is being processed.
         * @since 1.2
         */
        void onRecord(@Nonnull Data record);
    }

    /**
     * Sets a listener that is invoked for each record that is being bound.
     *
     * <p>This method can be used to optimize the binding of parameters to the SQL statement.</p>
     *
     * @param listener the consumer to invoke for each bind variable.
     * @since 1.2
     */
    void setRecordListener(@Nonnull RecordListener listener);
}
