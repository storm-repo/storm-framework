package st.orm.template;

import org.junit.jupiter.api.Test;
import st.orm.model.Address;
import st.orm.model.City;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.Visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetamodelTest {

    @Test
    public void testVisitPet() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        assertEquals("", model.path());
        assertEquals("pet", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(Visit.class, model.table().componentType());
        assertEquals(Pet.class, model.componentType());
    }

    @Test
    public void testVisitPetOwner() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner");
        assertEquals("pet", model.path());
        assertEquals("owner", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(Owner.class, model.componentType());
        assertEquals("", model.table().path());
        assertEquals("pet", model.table().component());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Pet.class, model.table().componentType());
    }

    @Test
    public void testVisitPetOwnerAddress() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address");
        assertEquals("pet.owner", model.path());
        assertEquals("address", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(Address.class, model.componentType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().component());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().componentType());
    }

    @Test
    public void testVisitPetOwnerAddressCity() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city");
        assertTrue(model.isColumn());
        assertEquals("pet.owner", model.path());
        assertEquals("address.city", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(City.class, model.componentType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().component());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().componentType());
    }

    @Test
    public void testOwnerAddress() throws SqlTemplateException {
        Metamodel< Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address");
        assertFalse(model.isColumn());
        assertEquals("pet.owner", model.path());
        assertEquals("address", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(Address.class, model.componentType());
        assertEquals("pet", model.table().path());
        assertEquals("owner", model.table().component());
        assertEquals(Visit.class, model.table().root());
        assertEquals(Owner.class, model.table().componentType());
    }

    @Test
    public void testVisitPetOwnerAddressCityName() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet.owner.address.city.name");
        assertTrue(model.isColumn());
        assertEquals("pet.owner.address.city", model.path());
        assertEquals("name", model.component());
        assertEquals(Visit.class, model.root());
        assertEquals(String.class, model.componentType());
        assertEquals("pet.owner", model.table().path());
        assertEquals("address.city", model.table().component());
        assertEquals(Visit.class, model.table().root());
        assertEquals(City.class, model.table().componentType());
    }
}
