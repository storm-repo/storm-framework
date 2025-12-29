package st.orm.core.template;

import org.junit.jupiter.api.Test;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Visit;
import st.orm.Metamodel;
import st.orm.core.model.Visit_;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetamodelTest {

    @Test
    public void testVisitPet() {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        assertEquals("", model.path());
        assertEquals("pet", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Visit.class, model.table().fieldType());
        assertEquals(Pet.class, model.fieldType());
    }

    @Test
    public void testVisitPetOwner() {
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
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isSame(a, b));
    }

    @Test
    public void testSameWithSameIdDifferentValues() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isSame(a, b)); // Should return true, because it should inspect PK only.
    }

    @Test
    public void testIdenticalWithSameInstance() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = a;
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertTrue(model.isIdentical(a, b));
    }

    @Test
    public void testIdenticalWithDifferentInstance() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.root(Pet.class);
        assertFalse(model.isIdentical(a, b));
    }

    @Test
    public void testSameOwnerWithSameIdSameValues() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertTrue(model.isSame(a, b));
    }

    @Test
    public void testSameOwnerWithSameIdDifferentValues() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertFalse(model.isSame(a, b));
    }

    @Test
    public void testIdenticalOwnerWithSameInstance() {
        Owner owner = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(owner).build();
        Pet b = Pet.builder().id(1).owner(owner).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertTrue(model.isIdentical(a, b));
    }

    @Test
    public void testIdenticalOwnerWithDifferentInstance() {
        Owner ownerA = Owner.builder().id(1).build();
        Owner ownerB = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(ownerA).build();
        Pet b = Pet.builder().id(1).owner(ownerB).build();
        Metamodel<Pet, ?> model = Metamodel.of(Pet.class, "owner");
        assertFalse(model.isIdentical(a, b));
    }

    @Test
    public void testSamePetWithSameIdSameValues() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.isSame(visitA, visitB));
    }

    @Test
    public void testSamePetWithSameIdDifferentValues() {
        Pet a = Pet.builder().id(1).owner(Owner.builder().id(1).build()).build();
        Pet b = Pet.builder().id(1).owner(Owner.builder().id(2).build()).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.isSame(visitA, visitB));  // Should return true, because it should inspect PK only.
    }

    @Test
    public void testPetOwnerWithSameInstance() {
        Owner owner = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(owner).build();
        Pet b = Pet.builder().id(1).owner(owner).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertTrue(Visit_.pet.owner.isIdentical(visitA, visitB));
    }

    @Test
    public void testIdenticalPetWithDifferentInstance() {
        Owner ownerA = Owner.builder().id(1).build();
        Owner ownerB = Owner.builder().id(1).build();
        Pet a = Pet.builder().id(1).owner(ownerA).build();
        Pet b = Pet.builder().id(1).owner(ownerB).build();
        Visit visitA = Visit.builder().id(1).pet(a).build();
        Visit visitB = Visit.builder().id(1).pet(b).build();
        assertFalse(Visit_.pet.owner.isIdentical(visitA, visitB));
    }
}
