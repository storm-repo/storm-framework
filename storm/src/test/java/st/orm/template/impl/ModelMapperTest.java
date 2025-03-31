package st.orm.template.impl;

import org.junit.jupiter.api.Test;
import st.orm.model.Address;
import st.orm.model.City;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.template.SqlTemplateException;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.util.AssertionErrors.assertNull;

public class ModelMapperTest {

    @Test
    public void testOwnerWithNullTelephone() throws SqlTemplateException {
        var model = ModelBuilder.newInstance().build(Owner.class, true);
        var mapper = ModelMapper.of(model);
        var result = mapper.map(Owner.builder()
                        .id(1)
                        .firstName("John")
                        .lastName("Doe")
                        .address(Address.builder()
                                .address("123 Street")
                                .city(City.builder().id(1).name("New York").build())
                                .build())
                .build());
        var values = new ArrayList<>(result.values());
        assertEquals(1, values.get(0));
        assertEquals("John", values.get(1));
        assertEquals("Doe", values.get(2));
        assertEquals("123 Street", values.get(3));
        assertEquals(1, values.get(4));
        assertNull("Telephone", values.get(5));
        assertEquals(0, values.get(6));
    }

    @Test
    public void testOwnerWithNullFirstName() throws SqlTemplateException {
        var model = ModelBuilder.newInstance().build(Owner.class, true);
        var mapper = ModelMapper.of(model);
        assertThrows(SqlTemplateException.class, () -> mapper.map(Owner.builder()
                .id(1)
                .lastName("Doe")
                .address(Address.builder()
                        .address("123 Street")
                        .city(City.builder().id(1).name("New York").build())
                        .build())
                .build()));
    }

    @Test
    public void testPet() throws SqlTemplateException {
        var model = ModelBuilder.newInstance().build(Pet.class, true);
        var mapper = ModelMapper.of(model);
        var result = mapper.map(
                Pet.builder()
                        .id(1)
                        .name("Rover")
                        .birthDate(LocalDate.of(2019, 1, 28))
                        .type(PetType.builder()
                                .id(2)
                                .name("Dog")
                                .build())
                        .owner(Owner.builder()
                                .id(3)
                                .firstName("John")
                                .lastName("Doe")
                                .address(Address.builder()
                                        .address("123 Street")
                                        .city(City.builder().id(1).name("New York").build())
                                        .build())
                                .build())
                        .build());
        var values = new ArrayList<>(result.values());
        assertEquals(1, values.get(0));
        assertEquals("Rover", values.get(1));
        assertEquals(LocalDate.of(2019, 1, 28), values.get(2));
        assertEquals(2, values.get(3));
        assertEquals(3, values.get(4));
    }

}
