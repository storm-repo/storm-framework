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
package st.orm.spi.oracle;

import jakarta.annotation.Nonnull;
import st.orm.core.repository.EntityRepository;
import st.orm.core.spi.EntityRepositoryProvider;
import st.orm.core.template.Model;
import st.orm.core.template.ORMTemplate;
import st.orm.Entity;

/**
 * Implementation of {@link EntityRepositoryProvider} for Oracle.
 */
public class OracleEntityRepositoryProviderImpl implements EntityRepositoryProvider {

    @Override
    public <ID, E extends Record & Entity<ID>> EntityRepository<E, ID> getEntityRepository(
            @Nonnull ORMTemplate ormTemplate,
            @Nonnull Model<E, ID> model) {
        return new OracleEntityRepositoryImpl<>(ormTemplate, model);
    }
}
