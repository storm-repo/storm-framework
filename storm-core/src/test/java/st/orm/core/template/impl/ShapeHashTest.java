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
package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shape identity groups executions of one template. It is declared as a {@code long}, so it has to carry 64
 * bits: folding the key through {@link Object#hashCode()} would cap it at 32 and render half of all identities
 * with a sign-extended prefix that says nothing about the shape.
 */
public class ShapeHashTest {

    @Test
    public void equalKeysShareAnIdentity() {
        assertEquals(ShapeHash.of(List.of("SELECT ", "x", " FROM ", "y")),
                ShapeHash.of(new ArrayList<>(List.of("SELECT ", "x", " FROM ", "y"))));
    }

    @Test
    public void orderSeparatesIdentities() {
        assertNotEquals(ShapeHash.of(List.of("a", "b")), ShapeHash.of(List.of("b", "a")));
    }

    @Test
    public void fragmentBoundariesSeparateIdentities() {
        assertNotEquals(ShapeHash.of(List.of("ab", "c")), ShapeHash.of(List.of("a", "bc")));
    }

    @Test
    public void nestingSeparatesIdentities() {
        assertNotEquals(ShapeHash.of(List.of("a", "b")), ShapeHash.of(List.of(List.of("a", "b"))));
    }

    @Test
    public void nullKeyIsTheUnknownShape() {
        assertEquals(0, ShapeHash.of(null));
    }

    @Test
    public void aDerivedIdentityIsNeverTheUnknownShape() {
        for (int i = 0; i < 20_000; i++) {
            assertNotEquals(0, ShapeHash.of(List.of("SELECT ", String.valueOf(i))));
        }
    }

    @Test
    public void identitiesUseTheFullWidth() {
        // A 32-bit hash widened to a long leaves the high word empty, or filled with sign extension. Neither
        // survives a sample of realistic keys.
        long high = 0;
        for (int i = 0; i < 1_000; i++) {
            high |= ShapeHash.of(List.of("SELECT ", "c" + i, " FROM t WHERE id = ", "?")) >>> 32;
        }
        assertEquals(0xffffffffL, high, "the high word should vary across shapes");
    }

    @Test
    public void distinctKeysRarelyCollide() {
        var identities = new HashSet<Long>();
        for (int i = 0; i < 50_000; i++) {
            identities.add(ShapeHash.of(List.of("SELECT ", "column" + i, " FROM table WHERE id = ?")));
        }
        assertEquals(50_000, identities.size());
    }

    @Test
    public void keysThatDifferOnlyDeepInsideSeparate() {
        assertNotEquals(ShapeHash.of(List.of("SELECT ", List.of("users", "u"), " WHERE x = ?")),
                ShapeHash.of(List.of("SELECT ", List.of("users", "v"), " WHERE x = ?")));
    }

    @Test
    public void aComponentWithoutTextFoldsThroughItsOwnEquality() {
        record Table(String name, int index) {}
        assertEquals(ShapeHash.of(List.of(new Table("users", 1))), ShapeHash.of(List.of(new Table("users", 1))));
        assertNotEquals(ShapeHash.of(List.of(new Table("users", 1))), ShapeHash.of(List.of(new Table("users", 2))));
    }

    @Test
    public void nullComponentsAreCarried() {
        var withNull = new ArrayList<>();
        withNull.add("a");
        withNull.add(null);
        assertNotEquals(ShapeHash.of(withNull), ShapeHash.of(List.of("a")));
        assertTrue(ShapeHash.of(withNull) != 0);
    }
}
