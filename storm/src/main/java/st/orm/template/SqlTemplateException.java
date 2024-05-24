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
package st.orm.template;

import java.sql.SQLException;

/**
 * Thrown to indicate an error occurred while processing a SQL template.
 */
public class SqlTemplateException extends SQLException {

    /**
     * Constructs a new exception with the specified reason.
     *
     * @param reason the reason for the exception.
     */
    public SqlTemplateException(String reason) {
        super(reason);
    }

    /**
     * Constructs a new exception with the specified reason and cause.
     *
     * @param cause the cause of the exception.
     */
    public SqlTemplateException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified reason and cause.
     *
     * @param reason the reason for the exception.
     * @param cause the cause of the exception.
     */
    public SqlTemplateException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
