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

import static java.util.stream.Collectors.joining;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import st.orm.Data;
import st.orm.core.template.Sql;
import st.orm.core.template.StatementOrigin;

/**
 * Logs statements as they execute.
 *
 * <p>Logging happens where every execution is intercepted exactly once, so a statement served by a compiled query
 * plan is reported on each execution, and a fetch is reported alongside the statement that led to it. Nothing has
 * to be annotated, and the statement is read as it was built, so query plans and the template cache stay in
 * effect.</p>
 *
 * <p>Statements log under {@code st.orm.sql}, and additionally under {@code st.orm.sql.<Type>} for the entity or
 * projection they target, so a single type can be turned up without the rest. At {@code DEBUG} the statement is
 * logged as it is sent, with placeholders. At {@code TRACE} the parameter values are rendered into it, producing a
 * statement that can be pasted into a database console.</p>
 *
 * <p><strong>Parameter values are database values</strong>, which may be sensitive. That is why they appear only at
 * {@code TRACE}: the level nobody enables in production by accident.</p>
 *
 * @since 1.13
 */
final class SqlStatementLogger {

    /** Root logger; the parent of the per-type loggers, so raising it raises every type. */
    private static final String ROOT_NAME = "st.orm.sql";

    private static final Logger ROOT = LoggerFactory.getLogger(ROOT_NAME);

    /**
     * Per-type loggers, resolved once per data type rather than per statement. A {@link ClassValue} associates the
     * logger with the class itself, so the data types of an undeployed application stay collectable.
     */
    private static final ClassValue<Logger> TYPE_LOGGERS = new ClassValue<>() {
        @Override
        protected Logger computeValue(Class<?> type) {
            return LoggerFactory.getLogger("%s.%s".formatted(ROOT_NAME, type.getSimpleName()));
        }
    };

    private SqlStatementLogger() {
    }

    /**
     * Logs the statement if the logger for its data type is enabled.
     *
     * @param sql the statement about to execute.
     */
    static void log(Sql sql) {
        Logger logger = loggerFor(sql.dataType().orElse(null));
        if (!logger.isDebugEnabled()) {
            return;
        }
        boolean inlineParameters = logger.isTraceEnabled();
        String statement = inlineParameters
                ? SqlLiterals.inline(sql.statement(), sql.parameters())
                : sql.statement();
        String message = "SQL (%s):%n%s".formatted(describe(sql), indent(statement));
        if (inlineParameters) {
            logger.trace(message);
        } else {
            logger.debug(message);
        }
    }

    /**
     * Returns the logger statements for the given data type report under, the root logger when the statement
     * targets no particular type.
     */
    private static Logger loggerFor(Class<? extends Data> dataType) {
        if (dataType == null) {
            return ROOT;
        }
        return TYPE_LOGGERS.get(dataType);
    }

    /**
     * Describes the statement for the log line: what it does, to what, and what caused it.
     */
    private static String describe(Sql sql) {
        String description = sql.dataType()
                .map(type -> "%s %s".formatted(sql.operation().name(), type.getSimpleName()))
                .orElseGet(() -> sql.operation().name());
        if (sql.origin() == StatementOrigin.FETCH) {
            return description + ", fetch";
        }
        return description;
    }

    private static String indent(String statement) {
        return statement.lines()
                .map(line -> "\t" + line)
                .collect(joining(System.lineSeparator()));
    }
}
