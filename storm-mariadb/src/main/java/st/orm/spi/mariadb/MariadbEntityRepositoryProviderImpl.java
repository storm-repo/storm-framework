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
package st.orm.spi.mariadb;

import jakarta.annotation.Nonnull;
import st.orm.repository.Entity;
import st.orm.repository.EntityModel;
import st.orm.repository.EntityRepository;
import st.orm.spi.EntityRepositoryProvider;
import st.orm.spi.Orderable.Before;
import st.orm.spi.mysql.MysqlEntityRepositoryProviderImpl;
import st.orm.template.ORMRepositoryTemplate;

/**
 * Implementation of {@link EntityRepositoryProvider} for MariaDB.
 */
@Before(MysqlEntityRepositoryProviderImpl.class)
public class MariadbEntityRepositoryProviderImpl implements EntityRepositoryProvider {

    @Override
    public <ID, E extends Entity<ID>> EntityRepository<E, ID> getEntityRepository(
            @Nonnull ORMRepositoryTemplate orm,
            @Nonnull EntityModel<E, ID> model) {
        return new MariadbEntityRepositoryImpl<>(orm, model);
    }
}
