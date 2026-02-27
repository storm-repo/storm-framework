package st.orm.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import st.orm.Metamodel;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.EntityWithNullableUK;
import st.orm.core.model.EntityWithNullableUK_;
import st.orm.core.model.EntityWithNullsNotDistinctUK_;
import st.orm.core.model.NullableCompoundUK;
import st.orm.core.model.Owner;
import st.orm.core.model.Owner_;
import st.orm.core.model.Pet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.VetSpecialty_;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;

public class MetamodelTest {

    @Test
    public void testVisitPet() {
        // "pet" is a direct field of Visit, so path is empty and field is "pet".
        // The table containing "pet" is Visit itself; fieldType is Pet.
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        assertEquals("", model.path());
        assertEquals("pet", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Visit.class, model.table().fieldType());
        assertEquals(Pet.class, model.fieldType());
    }

    @Test
    public void testVisitPetOwner() {
        // "pet.owner" navigates Visit -> Pet -> Owner. Path="pet", field="owner", table type=Pet.
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner");
        assertEquals("pet", model.path());
        assertEquals("owner", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Owner.class, model.fieldType());
        assertEquals("", model.table().path());
        assertEquals("pet", model.table().field());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Pet.class, model.table().fieldType());
    }

    @Test
    public void testVisitPetOwnerAddress() {
        // "pet.owner.address" navigates to Address (inlined in Owner). path="pet.owner", field="address".
        // Table is still Owner (address is inlined, not a separate table).
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address");
        assertEquals("pet.owner", model.path());
        assertEquals("address", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Address.class, model.fieldType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().field());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().fieldType());
    }

    @Test
    public void testVisitPetOwnerAddressCity() {
        // "pet.owner.address.city" is a FK column (City is a separate table referenced via Address).
        // isColumn=true because "address.city" is the composite field path within Owner's table.
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city");
        assertTrue(model.isColumn());
        assertEquals("pet.owner", model.path());
        assertEquals("address.city", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(City.class, model.fieldType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().field());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().fieldType());
    }

    @Test
    public void testOwnerAddress() {
        // Address is an inlined component (not a column or FK). isColumn=false.
        Metamodel< Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address");
        assertFalse(model.isColumn());
        assertEquals("pet.owner", model.path());
        assertEquals("address", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Address.class, model.fieldType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().field());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().fieldType());
    }

    @Test
    public void testVisitPetOwnerAddressCityName() {
        // "pet.owner.address.city.name" is a scalar column on the City table. isColumn=true.
        // path="pet.owner.address.city", field="name", fieldType=String, table type=City.
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city.name");
        assertTrue(model.isColumn());
        assertEquals("pet.owner.address.city", model.path());
        assertEquals("name", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(String.class, model.fieldType());
        assertEquals("pet.owner", model.table().path());
        assertEquals("address.city", model.table().field());
        assertEquals(Visit.class, model.table().root());
        assertEquals(City.class, model.table().fieldType());
    }

    @Test
    public void testSameWithSameIdSameValues() {
        // isSame compares PKs only. Two Pets with same id=1 should be "same" regardless of other fields.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isSame(a, b));
    }

    @Test
    public void testSameWithSameIdDifferentValues() {
        // isSame inspects PK only. Different owner ids (1 vs 2) don't affect sameness.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isSame(a, b));
    }

    @Test
    public void testIdenticalWithSameInstance() {
        // isIdentical uses reference identity (==). Same instance should be identical.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = a;
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isIdentical(a, b));
    }

    @Test
    public void testIdenticalWithDifferentInstance() {
        // isIdentical uses reference identity (==). Different instances, even with equal values, are not identical.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertFalse(model.isIdentical(a, b));
    }

    @Test
    public void testSameOwnerWithSameIdSameValues() {
        // isSame on the "owner" sub-path compares the owner PKs. Both have owner id=1; same.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertTrue(model.isSame(a, b));
    }

    @Test
    public void testSameOwnerWithSameIdDifferentValues() {
        // isSame on "owner" compares owner PKs. Owner id=1 vs id=2: not same.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertFalse(model.isSame(a, b));
    }

    @Test
    public void testIdenticalOwnerWithSameInstance() {
        // isIdentical on "owner" checks reference identity of the Owner field. Same instance: identical.
        Owner owner = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(owner).build();
        Pet b = Pet.builder().id(1).owner(owner).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertTrue(model.isIdentical(a, b));
    }

    @Test
    public void testIdenticalOwnerWithDifferentInstance() {
        // isIdentical on "owner" checks reference identity. Different instances: not identical.
        Owner ownerA = Owner.builder().id(1).build();
        Owner ownerB = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(ownerA).build();
        Pet b = Pet.builder().id(1).owner(ownerB).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertFalse(model.isIdentical(a, b));
    }

    @Test
    public void testSamePetWithSameIdSameValues() {
        // Visit_.pet.isSame compares the Pet PKs within Visit. Both pets have id=1: same.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.isSame(visitA, visitB));
    }

    @Test
    public void testSamePetWithSameIdDifferentValues() {
        // Visit_.pet.isSame inspects Pet PK only. Both pets have id=1, so same despite different owners.
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.isSame(visitA, visitB));
    }

    @Test
    public void testPetOwnerWithSameInstance() {
        // Visit_.pet.owner.isIdentical checks reference identity of the Owner within Visit's pet.
        // Same Owner instance shared by both Pets: identical.
        Owner owner = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(owner).build();
        Pet b = Pet.builder().id(1).owner(owner).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.owner.isIdentical(visitA, visitB));
    }

    @Test
    public void testIdenticalPetWithDifferentInstance() {
        // Visit_.pet.owner.isIdentical checks reference identity. Different Owner instances: not identical.
        Owner ownerA = Owner.builder().id(1).build();
        Owner ownerB = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(ownerA).build();
        Pet b = Pet.builder().id(1).owner(ownerB).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertFalse(Visit_.pet.owner.isIdentical(visitA, visitB));
    }

    // Compound key (inline record) metamodel tests.

    @Test
    public void testInlineRecordMetamodelImplementsKey() {
        // Non-Data inline record metamodels should implement Metamodel.Key.
        // VetSpecialty_.id is a compound PK (VetSpecialtyPK with int vetId, int specialtyId).
        assertInstanceOf(Metamodel.Key.class, VetSpecialty_.id);
        // Owner_.address is an inline Address record (not @UK, but still implements Key).
        assertInstanceOf(Metamodel.Key.class, Owner_.address);
    }

    @Test
    public void testCompoundKeyWithPrimitiveFieldsIsNotNullable() {
        // VetSpecialtyPK has only primitive int fields, so isNullable() should be false.
        Metamodel.Key<?, ?> key = VetSpecialty_.id;
        assertFalse(key.isNullable());
    }

    @Test
    public void testCompoundKeyWithNullableFieldsIsNullable() {
        // EntityWithNullableUK has @UK NullableCompoundUK(String userId, String email).
        // Both String fields are nullable (not primitive, not @Nonnull), so isNullable() should be true.
        assertInstanceOf(Metamodel.Key.class, EntityWithNullableUK_.uniqueKey);
        Metamodel.Key<?, ?> key = EntityWithNullableUK_.uniqueKey;
        assertTrue(key.isNullable());
    }

    @Test
    public void testCompoundKeyWithNullsNotDistinctIsNotNullable() {
        // EntityWithNullsNotDistinctUK has @UK(nullsDistinct = false) NullableCompoundUK.
        // Even though the constituent fields are nullable, nullsDistinct = false means
        // the database constraint prevents duplicate NULLs, so isNullable() should be false.
        assertInstanceOf(Metamodel.Key.class, EntityWithNullsNotDistinctUK_.uniqueKey);
        Metamodel.Key<?, ?> key = EntityWithNullsNotDistinctUK_.uniqueKey;
        assertFalse(key.isNullable());
    }

    @Test
    public void testNonUniqueInlineRecordIsNotNullable() {
        // Owner_.address is an inline record without @UK, so isNullable() defaults to false.
        Metamodel.Key<?, ?> key = Owner_.address;
        assertFalse(key.isNullable());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNullableCompoundKeyLeafReturnsNull() {
        // When the compound key is marked as effectively nullable, at least one flattened
        // leaf column must actually produce a null value from a record with a null constituent.
        Metamodel.Key<EntityWithNullableUK, ?> key = EntityWithNullableUK_.uniqueKey;
        assertTrue(key.isNullable());

        // Create an entity where one constituent field of the compound key is null.
        var entity = EntityWithNullableUK.builder().id(1)
                .uniqueKey(new NullableCompoundUK(null, "test@example.com")).build();

        var leaves = key.flatten();
        assertFalse(leaves.isEmpty());
        boolean anyLeafNull = leaves.stream()
                .anyMatch(leaf -> leaf.getValue(entity) == null);
        assertTrue(anyLeafNull,
                "At least one leaf column of a nullable compound key must produce a null value");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNonNullableCompoundKeyLeavesNeverNull() {
        // VetSpecialtyPK has only primitive int fields. All leaf columns should produce non-null values.
        Metamodel.Key<VetSpecialty, ?> key = VetSpecialty_.id;
        assertFalse(key.isNullable());

        var entity = VetSpecialty.builder()
                .id(new VetSpecialtyPK(1, 2))
                .build();

        var leaves = key.flatten();
        assertFalse(leaves.isEmpty());
        boolean allLeavesNonNull = leaves.stream()
                .allMatch(leaf -> leaf.getValue(entity) != null);
        assertTrue(allLeavesNonNull,
                "All leaf columns of a non-nullable compound key must produce non-null values");
    }
}
