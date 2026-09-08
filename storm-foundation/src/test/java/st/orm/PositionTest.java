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
package st.orm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.impl.PositionAccess;

class PositionTest {

    @Test
    void equalsOnValuesAndSide() {
        var position = new Position(List.of("Carter", 3), true);
        assertEquals(new Position(List.of("Carter", 3), true), position);
        assertEquals(new Position(List.of("Carter", 3), true).hashCode(), position.hashCode());
        assertNotEquals(new Position(List.of("Carter", 3), false), position);
        assertNotEquals(new Position(List.of("Carter", 4), true), position);
        assertNotEquals(new Position(List.of(3), true), position);
    }

    @Test
    void namesTheSideAndTheValues() {
        assertEquals("after [Carter, 3]", new Position(List.of("Carter", 3), true).toString());
        assertEquals("before [3]", new Position(List.of(3), false).toString());
    }

    @Test
    void needsAtLeastTheKeyValue() {
        assertThrows(IllegalArgumentException.class, () -> new Position(List.of(), true));
    }

    @Test
    void engineReadsTheValues() {
        assertEquals(List.of("Carter", 3), PositionAccess.values(new Position(List.of("Carter", 3), true)));
    }

    @Test
    void engineReaderRegistersOnce() {
        assertThrows(IllegalStateException.class, () -> PositionAccess.register(position -> List.of()));
    }
}
