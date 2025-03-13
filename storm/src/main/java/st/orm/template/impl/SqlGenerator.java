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

import st.orm.template.SqlTemplateException;

/**
 * Represents the SQL result of processing a template element.
 */
@FunctionalInterface
public interface SqlGenerator {

    /**
     * Returns the SQL string.
     *
     * @return the SQL string.
     * @throws SqlTemplateException if an error occurs while generating the SQL string.
     */
    String get() throws SqlTemplateException;
}
