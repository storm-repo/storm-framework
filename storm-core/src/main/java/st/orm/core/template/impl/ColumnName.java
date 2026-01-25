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

import jakarta.annotation.Nonnull;
import st.orm.core.spi.Name;
import st.orm.core.template.SqlDialect;

import static java.util.Objects.requireNonNull;

public record ColumnName(@Nonnull String name, boolean escape) implements Name {
    public ColumnName(@Nonnull String name) {
        this(name, false);
    }
    public ColumnName {
        requireNonNull(name, "name");
    }

    /**
     * Returns the qualified name of the database object. Depending on the object type, the qualified name may include
     * the schema and escape characters.
     *
     * @param dialect the SQL dialect.
     * @return the qualified name of the database object.
     */
    @Override
    public String qualified(@Nonnull SqlDialect dialect) {
        return escape ? dialect.escape(name) : dialect.getSafeIdentifier(name);
    }
}
