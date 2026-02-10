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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import st.orm.StormConfig;
import st.orm.core.template.SqlDialect;

/**
 * Provides the SQL dialect.
 *
 * @since 1.1
 */
public interface SqlDialectProvider extends Provider {

    /**
     * Returns the SQL dialect configured with the given {@link StormConfig}.
     *
     * @param config the Storm configuration to apply.
     * @return the SQL dialect.
     */
    SqlDialect getSqlDialect(@Nonnull StormConfig config);
}
