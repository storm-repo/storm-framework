package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Metamodel;
import st.orm.PersistenceException;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.Visit;

/**
 * Tests that exercise {@code MetamodelHelper} (via its public entry points
 * {@link Metamodel#root}, {@link Metamodel#of}, and {@link Metamodel#flatten})
 * and {@code EntityHelper} (via {@link st.orm.Entity#id}).
 *
 * <p>Both helper classes live in storm-foundation but require storm-core on the
 * classpath for their reflective lookups. This test ensures all code paths
 * through the helpers are exercised.</p>
 */
public class MetamodelHelperTest {

    // ---- Metamodel.root() via MetamodelHelper.root() ----

    @Test
    public void testRootReturnsSameInstanceForSameType() {
        Metamodel<City, City> first = Metamodel.root(City.class);
        Metamodel<City, City> second = Metamodel.root(City.class);
        assertSame(first, second, "root() should cache and return the same instance");
    }

    @Test
    public void testRootFieldTypeEqualsRootType() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        assertEquals(City.class, root.fieldType());
        assertEquals(City.class, root.root());
    }

    @Test
    public void testRootGetValueReturnsSameRecord() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        City city = City.builder().id(1).name("TestCity").build();
        assertEquals(city, root.getValue(city));
    }

    @Test
    public void testRootIsIdenticalForSameInstance() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        City city = City.builder().id(1).name("TestCity").build();
        assertTrue(root.isIdentical(city, city));
    }

    @Test
    public void testRootIsIdenticalFalseForDifferentInstances() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        City cityA = City.builder().id(1).name("TestCity").build();
        City cityB = City.builder().id(1).name("TestCity").build();
        assertFalse(root.isIdentical(cityA, cityB));
    }

    @Test
    public void testRootIsSameComparesById() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        City cityA = City.builder().id(1).name("CityA").build();
        City cityB = City.builder().id(1).name("CityB").build();
        assertTrue(root.isSame(cityA, cityB));
    }

    @Test
    public void testRootIsSameFalseForDifferentIds() {
        Metamodel<City, City> root = Metamodel.root(City.class);
        City cityA = City.builder().id(1).name("Same").build();
        City cityB = City.builder().id(2).name("Same").build();
        assertFalse(root.isSame(cityA, cityB));
    }

    @Test
    public void testRootForOwner() {
        Metamodel<Owner, Owner> root = Metamodel.root(Owner.class);
        assertNotNull(root);
        assertEquals(Owner.class, root.fieldType());
    }

    @Test
    public void testRootForEntityWithCompoundKey() {
        Metamodel<VetSpecialty, VetSpecialty> root = Metamodel.root(VetSpecialty.class);
        assertNotNull(root);
        assertEquals(VetSpecialty.class, root.fieldType());
    }

    // ---- Metamodel.of() via MetamodelHelper.of() ----

    @Test
    public void testOfSimpleScalarField() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        assertEquals(String.class, model.fieldType());
        assertEquals("name", model.field());
        assertEquals("", model.path());
        assertTrue(model.isColumn());
        assertFalse(model.isInline());
    }

    @Test
    public void testOfPrimaryKeyField() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "id");
        assertEquals(Integer.class, model.fieldType());
        assertEquals("id", model.field());
        assertTrue(model.isColumn());
    }

    @Test
    public void testOfForeignKeyField() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        assertEquals(Pet.class, model.fieldType());
        assertEquals("pet", model.field());
        assertEquals("", model.path());
        assertTrue(model.isColumn());
    }

    @Test
    public void testOfNestedPath() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner");
        assertEquals(Owner.class, model.fieldType());
        assertEquals("owner", model.field());
        assertEquals("pet", model.path());
    }

    @Test
    public void testOfDeepNestedPath() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city.name");
        assertEquals(String.class, model.fieldType());
        assertEquals("name", model.field());
        assertEquals("pet.owner.address.city", model.path());
        assertTrue(model.isColumn());
    }

    @Test
    public void testOfInlineField() {
        Metamodel<Owner, ?> model = Metamodel.of(Owner.class, "address");
        assertEquals(Address.class, model.fieldType());
        assertTrue(model.isInline());
        assertFalse(model.isColumn());
    }

    @Test
    public void testOfInlineNestedScalarField() {
        Metamodel<Owner, ?> model = Metamodel.of(Owner.class, "address.address");
        assertEquals(String.class, model.fieldType());
        assertTrue(model.isColumn());
    }

    @Test
    public void testOfInlineForeignKeyField() {
        Metamodel<Owner, ?> model = Metamodel.of(Owner.class, "address.city");
        assertEquals(City.class, model.fieldType());
        assertTrue(model.isColumn());
    }

    @Test
    public void testOfCachesSameInstance() {
        Metamodel<City, ?> first = Metamodel.of(City.class, "name");
        Metamodel<City, ?> second = Metamodel.of(City.class, "name");
        assertSame(first, second, "of() should cache and return the same instance");
    }

    @Test
    public void testOfInvalidPathThrows() {
        assertThrows(PersistenceException.class,
                () -> Metamodel.of(City.class, "nonExistentField"));
    }

    @Test
    public void testOfRootReference() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        assertEquals(City.class, model.root());
    }

    // ---- Metamodel.of() getValue() via MetamodelHelper.of() ----

    @Test
    public void testOfGetValueScalarField() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "name");
        City city = City.builder().id(1).name("Amsterdam").build();
        assertEquals("Amsterdam", model.getValue(city));
    }

    @Test
    public void testOfGetValuePrimaryKeyField() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "id");
        City city = City.builder().id(42).name("TestCity").build();
        assertEquals(42, model.getValue(city));
    }

    @Test
    public void testOfGetValueForeignKeyField() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Owner owner = Owner.builder().id(10).firstName("John").lastName("Doe")
                .address(Address.builder().build()).build();
        Pet pet = Pet.builder().id(1).owner(owner).build();
        assertEquals(owner, model.getValue(pet));
    }

    @Test
    public void testOfGetValueNestedField() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner.firstName");
        Owner owner = Owner.builder().id(10).firstName("Jane").lastName("Doe")
                .address(Address.builder().build()).build();
        Pet pet = Pet.builder().id(1).owner(owner).build();
        assertEquals("Jane", model.getValue(pet));
    }

    @Test
    public void testOfGetValueNullIntermediateReturnsNull() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner.firstName");
        Pet pet = Pet.builder().id(1).owner(null).build();
        assertEquals(null, model.getValue(pet));
    }

    // ---- Metamodel.of() isSame/isIdentical via MetamodelHelper.of() ----

    @Test
    public void testOfIsSameForScalarField() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "name");
        City cityA = City.builder().id(1).name("Same").build();
        City cityB = City.builder().id(2).name("Same").build();
        assertTrue(model.isSame(cityA, cityB));
    }

    @Test
    public void testOfIsSameFalseForDifferentValues() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "name");
        City cityA = City.builder().id(1).name("CityA").build();
        City cityB = City.builder().id(1).name("CityB").build();
        assertFalse(model.isSame(cityA, cityB));
    }

    @Test
    public void testOfIsIdenticalForSameReference() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Owner shared = Owner.builder().id(1).firstName("F").lastName("L")
                .address(Address.builder().build()).build();
        Pet petA = Pet.builder().id(1).owner(shared).build();
        Pet petB = Pet.builder().id(2).owner(shared).build();
        assertTrue(model.isIdentical(petA, petB));
    }

    @Test
    public void testOfIsIdenticalFalseForDifferentReferences() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Pet petA = Pet.builder().id(1).owner(
                Owner.builder().id(1).firstName("F").lastName("L")
                        .address(Address.builder().build()).build()).build();
        Pet petB = Pet.builder().id(2).owner(
                Owner.builder().id(1).firstName("F").lastName("L")
                        .address(Address.builder().build()).build()).build();
        assertFalse(model.isIdentical(petA, petB));
    }

    @Test
    public void testOfIsSameForForeignKeyComparesById() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Pet petA = Pet.builder().id(1).owner(
                Owner.builder().id(10).firstName("A").lastName("B")
                        .address(Address.builder().build()).build()).build();
        Pet petB = Pet.builder().id(2).owner(
                Owner.builder().id(10).firstName("C").lastName("D")
                        .address(Address.builder().build()).build()).build();
        assertTrue(model.isSame(petA, petB));
    }

    @Test
    public void testOfIsSameFalseForDifferentForeignKeys() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Pet petA = Pet.builder().id(1).owner(
                Owner.builder().id(10).firstName("A").lastName("B")
                        .address(Address.builder().build()).build()).build();
        Pet petB = Pet.builder().id(2).owner(
                Owner.builder().id(20).firstName("A").lastName("B")
                        .address(Address.builder().build()).build()).build();
        assertFalse(model.isSame(petA, petB));
    }

    // ---- flatten() via MetamodelHelper.flatten() ----

    @Test
    public void testFlattenNonInlineReturnsSingleton() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        List<Metamodel<City, ?>> flattened = model.flatten();
        assertEquals(1, flattened.size());
        assertSame(model, flattened.get(0));
    }

    @Test
    public void testFlattenInlineExpandsLeafFields() {
        Metamodel<Owner, ?> model = Metamodel.of(Owner.class, "address");
        assertTrue(model.isInline());
        List<Metamodel<Owner, ?>> flattened = model.flatten();
        assertEquals(2, flattened.size());
        assertTrue(flattened.stream().anyMatch(m -> m.field().contains("address")));
        assertTrue(flattened.stream().anyMatch(m -> m.field().contains("city")));
    }

    @Test
    public void testFlattenPrimaryKeyField() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "id");
        List<Metamodel<City, ?>> flattened = model.flatten();
        assertEquals(1, flattened.size());
    }

    @Test
    public void testFlattenCompoundPrimaryKey() {
        Metamodel<VetSpecialty, ?> model = Metamodel.of(VetSpecialty.class, "id");
        assertTrue(model.isInline());
        List<Metamodel<VetSpecialty, ?>> flattened = model.flatten();
        assertEquals(2, flattened.size());
    }

    @Test
    public void testFlattenForeignKeyExpandsEntityColumns() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        List<Metamodel<Visit, ?>> flattened = model.flatten();
        // Flattening a FK expands the referenced entity's column structure.
        assertTrue(flattened.size() > 1,
                "Flattening a FK metamodel should expand to the referenced entity's columns");
    }

    // ---- canonical() (uses MetamodelHelper.of() internally) ----

    @Test
    public void testCanonicalForNestedPath() {
        Metamodel<Visit, ?> nested = Metamodel.of(Visit.class, "pet.owner");
        Metamodel<?, ?> canonical = nested.canonical();
        assertEquals(Pet.class, canonical.root());
        assertEquals("owner", canonical.field());
    }

    @Test
    public void testCanonicalForDirectField() {
        Metamodel<City, ?> direct = Metamodel.of(City.class, "name");
        Metamodel<?, ?> canonical = direct.canonical();
        assertEquals(City.class, canonical.root());
        assertEquals("name", canonical.field());
    }

    // ---- fieldPath() ----

    @Test
    public void testFieldPathForDirectField() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        assertEquals("name", model.fieldPath());
    }

    @Test
    public void testFieldPathForNestedPath() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner");
        assertEquals("pet.owner", model.fieldPath());
    }

    @Test
    public void testFieldPathForDeepNestedPath() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city.name");
        assertEquals("pet.owner.address.city.name", model.fieldPath());
    }

    // ---- Entity.id() via EntityHelper.getId() ----

    @Test
    public void testEntityIdReturnsCorrectPrimaryKey() {
        City city = City.builder().id(42).name("TestCity").build();
        assertEquals(42, city.id());
    }

    @Test
    public void testEntityIdForNullPrimaryKey() {
        City city = City.builder().id(null).name("TestCity").build();
        assertEquals(null, city.id());
    }

    @Test
    public void testEntityIdForOwner() {
        Owner owner = Owner.builder().id(7).firstName("John").lastName("Doe")
                .address(Address.builder().build()).build();
        assertEquals(7, owner.id());
    }

    @Test
    public void testEntityIdForCompoundKey() {
        VetSpecialtyPK compoundKey = new VetSpecialtyPK(3, 5);
        VetSpecialty vetSpecialty = new VetSpecialty(compoundKey);
        assertEquals(compoundKey, vetSpecialty.id());
    }

    @Test
    public void testEntityIdForZero() {
        City city = City.builder().id(0).name("TestCity").build();
        assertEquals(0, city.id());
    }

    @Test
    public void testEntityIdForPet() {
        Pet pet = Pet.builder().id(99).build();
        assertEquals(99, pet.id());
    }

    // ---- Metamodel.key() factory ----

    @Test
    public void testKeyWrapsNonKeyMetamodel() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        assertNotNull(key);
        assertEquals(model.fieldType(), key.fieldType());
        assertEquals(model.field(), key.field());
        assertEquals(model.path(), key.path());
        assertFalse(key.isNullable());
    }

    @Test
    public void testKeyReturnsSameInstanceForExistingKey() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        Metamodel.Key<City, ?> doubleWrapped = Metamodel.key(key);
        assertSame(key, doubleWrapped, "key() should return the same Key instance");
    }

    @Test
    public void testKeyDelegateGetValue() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, Object> key = Metamodel.key(model);
        City city = City.builder().id(1).name("KeyCity").build();
        assertEquals("KeyCity", key.getValue(city));
    }

    @Test
    public void testKeyDelegateFlatten() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        List<Metamodel<City, ?>> flattened = key.flatten();
        assertEquals(1, flattened.size());
    }

    @Test
    public void testKeyDelegateIsSame() {
        Metamodel<City, Object> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, Object> key = Metamodel.key(model);
        City cityA = City.builder().id(1).name("Same").build();
        City cityB = City.builder().id(2).name("Same").build();
        assertTrue(key.isSame(cityA, cityB));
    }

    @Test
    public void testKeyDelegateIsIdentical() {
        Metamodel<Pet, Object> model = Metamodel.of(Pet.class, "owner");
        Metamodel.Key<Pet, Object> key = Metamodel.key(model);
        Owner shared = Owner.builder().id(1).firstName("F").lastName("L")
                .address(Address.builder().build()).build();
        Pet petA = Pet.builder().id(1).owner(shared).build();
        Pet petB = Pet.builder().id(2).owner(shared).build();
        assertTrue(key.isIdentical(petA, petB));
    }

    @Test
    public void testKeyDelegateEqualsWrappedMetamodel() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        assertEquals(key, model);
    }

    @Test
    public void testKeyDelegateHashCodeMatchesWrappedMetamodel() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        assertEquals(model.hashCode(), key.hashCode());
    }

    @Test
    public void testKeyDelegateToString() {
        Metamodel<City, ?> model = Metamodel.of(City.class, "name");
        Metamodel.Key<City, ?> key = Metamodel.key(model);
        assertEquals(model.toString(), key.toString());
    }
}
