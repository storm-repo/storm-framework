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
package st.orm.kotlin;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import kotlin.reflect.KClass;

import java.util.stream.Stream;

public interface KPreparedQuery extends KQuery, AutoCloseable {

    /**
     * Returns the generated keys as result of an insert statement. Returns an empty stream if the statement did not
     * generate any keys.
     *
     * @return the generated keys as result of an insert statement.
     * @throws PersistenceException if the statement fails.
     */
    <ID> Stream<ID> getGeneratedKeys(@Nonnull KClass<ID> type);

    /**
     * Close the resources associated with this query.
     *
     * @throws PersistenceException if the resource cannot be closed.
     */
    @Override
    void close();
}
