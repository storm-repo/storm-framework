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

import st.orm.spi.Orderable.Before;
import st.orm.spi.SqlDialect;
import st.orm.spi.SqlDialectProvider;
import st.orm.spi.mysql.MysqlSqlDialectProviderImpl;

/**
 * Implementation of {@link SqlDialectProvider} for MariaDB.
 */
@Before(MysqlSqlDialectProviderImpl.class)
public class MariadbSqlDialectProviderImpl implements SqlDialectProvider {

    @Override
    public SqlDialect getSqlDialect() {
        return new MariadbSqlDialect();
    }
}