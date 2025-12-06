package st.orm.core.template;

import org.junit.jupiter.api.Test;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.Visit;
import st.orm.Metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetamodelTest {

    @Test
    public void testVisitPet() throws SqlTemplateException {
        Metamodel<Visit, ?> model = Metamodel.of(Visit.class, "pet");
        assertEquals("", model.path());
        assertEquals("pet", model.field());
        assertEquals(Visit.class, model.root());
        assertEquals(Visit.class, model.table().fieldType());
        assertEquals(Pet.class, model.fieldType());
    }

    @Test
    public void testVisitPetOwner() throws SqlTemplateException {
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
    public void testVisitPetOwnerAddress() throws SqlTemplateException {
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
    public void testVisitPetOwnerAddressCity() throws SqlTemplateException {
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
    public void testOwnerAddress() throws SqlTemplateException {
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
    public void testVisitPetOwnerAddressCityName() throws SqlTemplateException {
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
}
