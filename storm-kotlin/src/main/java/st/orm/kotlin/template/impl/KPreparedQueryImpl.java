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
package st.orm.kotlin.template.impl;

import jakarta.annotation.Nonnull;
import kotlin.reflect.KClass;
import st.orm.PreparedQuery;
import st.orm.kotlin.KPreparedQuery;
import st.orm.spi.ORMReflection;
import st.orm.spi.Providers;

import java.util.stream.Stream;


public class KPreparedQueryImpl extends KQueryImpl implements KPreparedQuery {
    private final static ORMReflection REFLECTION = Providers.getORMReflection();

    private final PreparedQuery preparedQuery;

    public KPreparedQueryImpl(@Nonnull PreparedQuery preparedQuery) {
        super(preparedQuery);
        this.preparedQuery = preparedQuery;
    }

    @Override
    public <ID> Stream<ID> getGeneratedKeys(@Nonnull KClass<ID> type) {
        //noinspection unchecked
        return preparedQuery.getGeneratedKeys((Class<ID>) REFLECTION.getType(type));
    }

    @Override
    public void close() {
        preparedQuery.close();
    }
}
