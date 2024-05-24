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
import st.orm.spi.mysql.MysqlEntityRepositoryImpl;
import st.orm.template.ORMRepositoryTemplate;

/**
 * Implementation of {@link st.orm.repository.EntityRepository} for MariaDB.
 */
public class MariadbEntityRepositoryImpl<E extends Entity<ID>, ID> extends MysqlEntityRepositoryImpl<E, ID> {

    public MariadbEntityRepositoryImpl(@Nonnull ORMRepositoryTemplate orm, @Nonnull EntityModel<E, ID> model) {
        super(orm, model);
    }
}
