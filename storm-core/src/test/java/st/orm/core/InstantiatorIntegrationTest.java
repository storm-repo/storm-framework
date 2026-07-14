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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.spi.Instantiators;

/**
 * Verifies that the metamodel processor generates and registers {@link st.orm.mapping.Instantiator}
 * implementations for the test models, and that they construct instances equivalent to the canonical constructor.
 * The full test suite exercises these instantiators implicitly, as the row mapper dispatches to them instead of
 * reflective construction.
 */
public class InstantiatorIntegrationTest {

    @Test
    public void testInstantiatorsAreRegisteredForModelRecords() {
        assertNotNull(Instantiators.find(Pet.class));
        assertNotNull(Instantiators.find(Owner.class));
        assertNotNull(Instantiators.find(City.class));
        // Plain nested records (no Data interface) are covered through the referenced-record expansion.
        assertNotNull(Instantiators.find(Address.class));
    }

    @Test
    public void testInstantiatorConstructsEquivalentInstance() {
        var instantiator = Instantiators.find(City.class);
        assertNotNull(instantiator);
        assertSame(City.class, instantiator.type());
        City city = instantiator.instantiate(new Object[] { 42, "Rotterdam" });
        assertEquals(new City(42, "Rotterdam"), city);
    }

    @Test
    public void testUnregisteredTypeFallsBackToNull() {
        assertNull(Instantiators.find(String.class));
    }
}
