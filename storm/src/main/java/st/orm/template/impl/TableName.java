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

import jakarta.annotation.Nonnull;
import st.orm.spi.Name;
import st.orm.template.SqlDialect;

import static java.util.Objects.requireNonNull;

public record TableName(@Nonnull String table, @Nonnull String schema, boolean escape) implements Name {
    public TableName {
        requireNonNull(table, "table");
        requireNonNull(schema, "schema");
        if (table.isEmpty()) {
            throw new IllegalArgumentException("name");
        }
    }

    @Override
    public String name() {
        return table;
    }

    @Override
    public String getQualifiedName(@Nonnull SqlDialect dialect) {
        String schema = this.schema.isEmpty()
                ? ""
                : escape
                    ? dialect.escape(this.schema)
                    : dialect.getSafeIdentifier(this.schema);
        String table = escape
                ? dialect.escape(this.table)
                : dialect.getSafeIdentifier(this.table);
        return schema.isEmpty()
                ? table
                : STR."\{schema}.\{table}";
    }
}
