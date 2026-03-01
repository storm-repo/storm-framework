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
import st.orm.Data;

/**
 * Represents a reference to a table qualified with an alias.
 *
 * <p>An {@code AliasedTable} identifies a table as it appears in a SQL query, using a table name and an alias. It does
 * not represent a table definition, but a reference that can be rendered into SQL.</p>
 *
 * <p>The table name and alias together form the logical identity of this object. Rendering concerns such as identifier
 * escaping are handled when producing the SQL representation.</p>
 *
 * @param type the type of the table.
 * @param name the table name.
 * @param alias the alias used to reference the table.
 * @since 1.7
 */
public record AliasedTable(@Nonnull Class<? extends Data> type, @Nonnull String name, @Nonnull String alias) {}
