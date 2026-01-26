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

import st.orm.core.template.SqlTemplateException;

/**
 * Runtime wrapper for {@link SqlTemplateException}.
 *
 * <p>This exception is used to rethrow a {@link SqlTemplateException} in contexts where checked exceptions are not
 * allowed or would be inconvenient, such as within functional pipelines or callback-based APIs.</p>
 *
 * <p>The wrapped exception is considered an internal transport mechanism. Instances of this type are always expected to
 * be unwrapped back to {@link SqlTemplateException} before being exposed to API boundaries.</p>
 *
 * @since 1.8
 */
final class UncheckedSqlTemplateException extends RuntimeException {

    private final SqlTemplateException cause;

    /**
     * Creates a new unchecked wrapper for the given {@link SqlTemplateException}.
     *
     * @param e the checked exception to wrap
     */
    public UncheckedSqlTemplateException(SqlTemplateException e) {
        super(e);
        this.cause = e;
    }

    /**
     * Returns the original {@link SqlTemplateException}.
     *
     * @return the wrapped checked exception
     */
    @Override
    public SqlTemplateException getCause() {
        return cause;
    }
}