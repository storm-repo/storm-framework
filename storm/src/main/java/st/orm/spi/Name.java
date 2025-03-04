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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import st.orm.template.SqlDialect;

/**
 * Represents a name of a database object.
 *
 * @since 1.2
 */
public interface Name {

    /**
     * Returns the name of the database object.
     *
     * @return the name of the database object.
     */
    String name();

    /**
     * Returns the qualified name of the database object. Depending on the object type, the qualified name may include
     * the schema and escape characters.
     *
     * @param dialect the SQL dialect.
     * @return the qualified name of the database object.
     */
    String getQualifiedName(@Nonnull SqlDialect dialect);
}
