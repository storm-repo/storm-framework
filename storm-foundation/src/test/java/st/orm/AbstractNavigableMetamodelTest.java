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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the navigation-only metamodel node that generated metamodels use past a {@code Ref} boundary: it locates a
 * column through root, table and path exactly as a full metamodel does, and compares equal to the full metamodel of
 * the same field, without offering value extraction.
 */
class AbstractNavigableMetamodelTest {

    record Owner(int id, Address address) implements Data {}
    record Address(String street, City city) implements Data {}
    record City(int id, String name) implements Data {}

    /** A navigable node; the base class needs no override, so a plain subclass stands in for a generated one. */
    static class Node<E> extends AbstractNavigableMetamodel<Owner, E> {
        Node(Class<E> fieldType, String path, String field, boolean inline, Navigable<Owner, ?> parent) {
            super(fieldType, path, field, inline, parent);
        }
    }

    /** The full metamodel of the same field, the counterpart a navigable node must compare equal to. */
    static class FullMetamodel<E> extends AbstractMetamodel<Owner, E, E> {
        FullMetamodel(Class<E> fieldType, String path, String field, boolean inline, Metamodel<Owner, ?> parent) {
            super(fieldType, path, field, inline, parent);
        }

        @Override
        public E getValue(Owner record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isIdentical(Owner a, Owner b) {
            return a == b;
        }

        @Override
        public boolean isSame(Owner a, Owner b) {
            return a.equals(b);
        }
    }

    private final Node<Owner> owner = new Node<>(Owner.class, "", "", false, null);
    private final Node<Address> address = new Node<>(Address.class, "", "address", true, owner);
    private final Node<City> city = new Node<>(City.class, "address", "city", false, address);
    private final Node<String> cityName = new Node<>(String.class, "address.city", "name", false, city);

    @Test
    void rootIsTheRootOfTheChainAndTheFieldTypeOfARootNode() {
        assertEquals(Owner.class, owner.root());
        assertEquals(Owner.class, cityName.root());
        assertEquals(Owner.class, owner.fieldType());
        assertEquals(String.class, cityName.fieldType());
    }

    @Test
    void tableIsTheNodeItselfForARootAndTheNearestNonInlineParentOtherwise() {
        assertSame(owner, owner.table());
        // The inline address record contributes no table of its own, so its columns belong to the owner table.
        assertSame(owner, address.table());
        assertSame(owner, city.table());
        assertSame(city, cityName.table());
        assertEquals(City.class, cityName.tableType());
    }

    @Test
    void inlineAndColumnFollowTheConstructorArguments() {
        assertTrue(address.isInline());
        assertFalse(address.isColumn(), "an inline component is not a column");
        assertFalse(owner.isColumn(), "a root node names no field and is not a column");
        assertFalse(city.isInline());
        assertTrue(city.isColumn());
        assertEquals("address.city.name", cityName.fieldPath());
    }

    @Test
    void equalsAndHashCodeFollowTableTypePathAndField() {
        Node<String> sameField = new Node<>(String.class, "address.city", "name", false, city);
        assertEquals(cityName, cityName);
        assertEquals(cityName, sameField);
        assertEquals(cityName.hashCode(), sameField.hashCode());
        assertEquals(cityName.hashCode(), cityName.hashCode(), "the hash is cached and stable");
        assertNotEquals(cityName, new Node<>(Integer.class, "address.city", "id", false, city));
        assertNotEquals(cityName, new Node<>(String.class, "city", "name", false, city));
        assertNotEquals(cityName, "address.city.name");
    }

    @Test
    void equalsAFullMetamodelOfTheSameField() {
        // The full metamodel of owner.address.city.name reaches the same field through the same path, so a
        // predicate built on either finds the same column. Equality is checked from the navigable side: a full
        // metamodel only recognizes other full metamodels.
        FullMetamodel<Owner> ownerMetamodel = new FullMetamodel<>(Owner.class, "", "", false, null);
        FullMetamodel<Address> addressMetamodel = new FullMetamodel<>(Address.class, "", "address", true, ownerMetamodel);
        FullMetamodel<City> cityMetamodel = new FullMetamodel<>(City.class, "address", "city", false, addressMetamodel);
        FullMetamodel<String> cityNameMetamodel =
                new FullMetamodel<>(String.class, "address.city", "name", false, cityMetamodel);
        assertEquals(cityName, cityNameMetamodel);
        assertEquals(cityName.hashCode(), cityNameMetamodel.hashCode());
    }

    @Test
    void toStringNamesRootTypePathAndField() {
        assertEquals("Navigable{root=Owner, type=String, path='address.city', field='name'}", cityName.toString());
    }
}
