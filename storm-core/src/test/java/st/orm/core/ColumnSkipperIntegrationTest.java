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
package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.template.ORMTemplate;

/**
 * Verifies that the row reader skips decoding the non-key columns of entities that are already interned within the
 * query. The pet graph joins each pet to its owner and the owner's city, so owners and cities repeat across rows;
 * only their first occurrence should decode the full column region, subsequent rows should decode the primary key
 * alone.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@JdbcTest
public class ColumnSkipperIntegrationTest {

    // Flat column layout of the Pet graph: pet (id, name, birth_date, type ref), then the owner region
    // (id, first_name, last_name, address, city region (id, name), telephone, version).
    private static final int PET_COLUMNS = 4;
    private static final int OWNER_PREFIX_COLUMNS = 3;  // Owner columns between the owner PK and the city region.
    private static final int CITY_COLUMNS = 2;
    private static final int OWNER_SUFFIX_COLUMNS = 2;  // Owner columns after the city region.

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSkipsDecodingColumnsOfInternedEntities() throws Exception {
        var counter = new ColumnReadCounter();
        List<Pet> pets;
        try (var connection = dataSource.getConnection()) {
            pets = ORMTemplate.of(counter.wrap(connection)).entity(Pet.class).select().getResultList();
        }

        // Repeated owners and cities must resolve to the same instance (query-scoped interning).
        Map<Integer, Owner> ownersById = new HashMap<>();
        Map<Integer, City> citiesById = new HashMap<>();
        for (Pet pet : pets) {
            Owner owner = pet.owner();
            if (owner != null) {
                assertSame(ownersById.merge(owner.id(), owner, (a, b) -> a), owner);
                City city = owner.address().city();
                if (city != null) {
                    assertSame(citiesById.merge(city.id(), city, (a, b) -> a), city);
                }
            }
        }

        // Hydration must be unaffected by the skipped columns.
        var reference = ORMTemplate.of(dataSource).entity(Pet.class).select().getResultList();
        assertEquals(Set.copyOf(reference), Set.copyOf(pets));

        // Each row decodes the pet columns and the owner PK. The remaining owner and city columns are only decoded
        // on their first occurrence; when the owner PK is null (left join), the full region decodes as usual.
        Set<Integer> seenOwners = new HashSet<>();
        Set<Integer> seenCities = new HashSet<>();
        int expected = 0;
        for (Pet pet : pets) {
            expected += PET_COLUMNS + 1;
            Owner owner = pet.owner();
            if (owner == null) {
                expected += OWNER_PREFIX_COLUMNS + CITY_COLUMNS + OWNER_SUFFIX_COLUMNS;
                continue;
            }
            if (!seenOwners.add(owner.id())) {
                continue;
            }
            expected += OWNER_PREFIX_COLUMNS + 1 + OWNER_SUFFIX_COLUMNS;
            City city = owner.address().city();
            if (city == null || seenCities.add(city.id())) {
                expected += CITY_COLUMNS - 1;
            }
        }
        assertEquals(expected, counter.reads().get());
    }

    /**
     * Wraps a connection so that every positional column getter on the result sets it produces is counted.
     */
    private static final class ColumnReadCounter {
        private final AtomicInteger reads = new AtomicInteger();

        AtomicInteger reads() {
            return reads;
        }

        Connection wrap(Connection connection) {
            return (Connection) proxy(connection, Connection.class);
        }

        private Object proxy(Object target, Class<?> type) {
            InvocationHandler handler = (proxyInstance, method, args) -> {
                if (type == ResultSet.class
                        && method.getName().startsWith("get")
                        && args != null && args.length > 0 && args[0] instanceof Integer) {
                    reads.incrementAndGet();
                }
                Object result;
                try {
                    result = method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
                if (result instanceof ResultSet resultSet) {
                    return proxy(resultSet, ResultSet.class);
                }
                if (result instanceof PreparedStatement statement) {
                    return proxy(statement, PreparedStatement.class);
                }
                if (result instanceof Statement statement) {
                    return proxy(statement, Statement.class);
                }
                if (result instanceof Connection connection) {
                    return proxy(connection, Connection.class);
                }
                return result;
            };
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { type }, handler);
        }
    }
}
