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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The component set backs the native-image registrations: compound primary keys, inline
 * components, and types reached through generic constructor signatures are introspected like the
 * Data types that carry them, while JDK and Kotlin platform types are covered by their own metadata.
 */
class TypeDiscoveryComponentTest {

    record CityPk(String countryCode, int cityCode) {
    }

    record Photo(String url) {
    }

    record Landmark(String name, Photo photo) {
    }

    record City(CityPk id, String name, BigDecimal area, List<Photo> photos,
                Map<String, List<Landmark>> landmarksByDistrict) {
    }

    @Test
    void componentDiscoveryContainsCompoundKeyAndGenericArguments() {
        List<Class<?>> components = TypeDiscovery.getComponentTypes(City.class);
        assertTrue(components.contains(CityPk.class), "compound primary key");
        assertTrue(components.contains(Photo.class), "generic type argument");
        assertTrue(components.contains(Landmark.class), "nested generic type argument");
    }

    @Test
    void componentDiscoveryWalksComponentsRecursively() {
        List<Class<?>> components = TypeDiscovery.getComponentTypes(Landmark.class);
        assertEquals(List.of(Photo.class), components);
    }

    @Test
    void componentDiscoveryExcludesTheTypeItselfAndPlatformTypes() {
        List<Class<?>> components = TypeDiscovery.getComponentTypes(City.class);
        assertFalse(components.contains(City.class), "the type itself");
        assertFalse(components.contains(String.class), "JDK value type");
        assertFalse(components.contains(BigDecimal.class), "JDK value type");
        assertFalse(components.contains(List.class), "JDK collection type");
        assertFalse(components.contains(Map.class), "JDK collection type");
    }

    @Test
    void componentDiscoveryOfALeafTypeIsEmpty() {
        assertEquals(List.of(), TypeDiscovery.getComponentTypes(Photo.class));
    }
}
