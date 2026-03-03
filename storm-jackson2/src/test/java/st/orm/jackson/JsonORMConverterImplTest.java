package st.orm.jackson;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.ORMTemplate.of;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.sql.DataSource;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Json;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.jackson.model.Address;

/**
 * Tests for {@link st.orm.jackson.spi.JsonORMConverterImpl} targeting uncovered branches:
 * <ul>
 *   <li>Custom @JsonSerialize/@JsonDeserialize annotations on @Json fields</li>
 *   <li>Sealed type (polymorphic) JSON fields with @JsonTypeName</li>
 *   <li>toDatabase with null record (null field value path)</li>
 *   <li>failOnUnknown and failOnMissing enabled paths</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class JsonORMConverterImplTest {

    @Autowired
    private DataSource dataSource;

    // Custom Serializer/Deserializer for Address

    public static class AddressSerializer extends JsonSerializer<Address> {
        @Override
        public void serialize(Address value, JsonGenerator gen, SerializerProvider serializers)
                throws java.io.IOException {
            gen.writeString(value.address() + " | " + value.city());
        }
    }

    public static class AddressDeserializer extends JsonDeserializer<Address> {
        @Override
        public Address deserialize(JsonParser parser, DeserializationContext context)
                throws java.io.IOException {
            String text = parser.getText();
            String[] parts = text.split(" \\| ");
            return new Address(parts[0], parts[1]);
        }
    }

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithCustomSerializers(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json
            @JsonSerialize(using = AddressSerializer.class)
            @JsonDeserialize(using = AddressDeserializer.class)
            Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void customJsonSerializerAndDeserializerShouldBeUsedForJsonField() {
        // Exercises the custom @JsonSerialize/@JsonDeserialize annotation branches
        // in JsonORMConverterImpl constructor (lines 106-126 in jackson2).
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithCustomSerializers.class);
        var address = new Address("123 Main St", "Springfield");
        var owner = OwnerWithCustomSerializers.builder()
                .firstName("Test")
                .lastName("User")
                .address(address)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        // The custom serializer stores "address | city" as a plain string.
        // The custom deserializer should parse it back.
        assertEquals("123 Main St", inserted.address().address());
        assertEquals("Springfield", inserted.address().city());
    }

    @Test
    public void customSerializerAndDeserializerShouldRoundTripThroughDatabase() {
        // Exercises both custom serializer (write) and deserializer (read) in a full round trip.
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithCustomSerializers.class);
        var address = new Address("789 Pine Rd", "Seattle");
        var owner = OwnerWithCustomSerializers.builder()
                .firstName("Round")
                .lastName("Trip")
                .address(address)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertEquals("789 Pine Rd", inserted.address().address());
        assertEquals("Seattle", inserted.address().city());

        // Verify another read from DB also uses the custom deserializer.
        var fetched = repository.getById(inserted.id());
        assertEquals("789 Pine Rd", fetched.address().address());
        assertEquals("Seattle", fetched.address().city());
    }

    // Sealed type / polymorphic JSON

    @JsonTypeInfo(use = NAME)
    public sealed interface PolymorphicPerson permits PersonA, PersonB {}

    public record PersonA(String firstName, String lastName) implements PolymorphicPerson {}

    public record PersonB(String firstName, String lastName) implements PolymorphicPerson {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithPolymorphicPerson(
            @PK Integer id,
            @Nonnull @Json PolymorphicPerson person,
            @Nonnull @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void polymorphicJsonDeserializationShouldResolveCorrectSubtypeViaDiscriminator() {
        // Exercises the sealed type branch (type != null) in JsonORMConverterImpl constructor.
        // The sealed interface PolymorphicPerson has permitted subtypes PersonA and PersonB.
        var orm = of(dataSource);
        var query = orm.query("SELECT id, JSON_OBJECT('@type' VALUE 'PersonA', 'firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner");
        var owners = query.getResultList(OwnerWithPolymorphicPerson.class);
        assertEquals(10, owners.size());
        assertTrue(owners.stream().allMatch(x -> x.person instanceof PersonA));
    }

    // Sealed interface with @JsonTypeName annotations on subtypes

    @JsonTypeInfo(use = NAME)
    public sealed interface NamedPerson permits NamedPersonA, NamedPersonB {}

    @JsonTypeName("A")
    public record NamedPersonA(String firstName, String lastName) implements NamedPerson {}

    @JsonTypeName("B")
    public record NamedPersonB(String firstName, String lastName) implements NamedPerson {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithNamedPolymorphicPerson(
            @PK Integer id,
            @Nonnull @Json NamedPerson person,
            @Nonnull @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void polymorphicJsonWithExplicitTypeNamesShouldResolveSubtype() {
        // Exercises the branch in getPermittedSubtypes where typeNameAnnotation is non-null.
        var orm = of(dataSource);
        var query = orm.query("SELECT id, JSON_OBJECT('@type' VALUE 'A', 'firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner");
        var owners = query.getResultList(OwnerWithNamedPolymorphicPerson.class);
        assertEquals(10, owners.size());
        assertTrue(owners.stream().allMatch(x -> x.person instanceof NamedPersonA));
    }

    // Nullable JSON field (toDatabase null path)

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithNullableAddress(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nullable @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void insertEntityWithNullJsonFieldShouldPersistNullAndReadBackAsNull() {
        // Exercises the toDatabase path when the @Json field value is null.
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithNullableAddress.class);
        var owner = OwnerWithNullableAddress.builder()
                .firstName("NullAddr")
                .lastName("Test")
                .address(null)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertNull(inserted.address());
    }

    @Test
    public void selectEntityWithNullJsonFieldShouldReturnNull() {
        // Insert a row with null address directly, then select it.
        var orm = of(dataSource);
        orm.query("INSERT INTO owner (first_name, last_name, address, telephone) VALUES ('NullSel', 'Test', NULL, '555')")
                .executeUpdate();
        var result = orm.query("SELECT id, first_name, last_name, address, telephone FROM owner WHERE first_name = 'NullSel'")
                .getSingleResult(OwnerWithNullableAddress.class);
        assertNull(result.address());
    }

    // failOnUnknown enabled

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithFailOnUnknown(
            @PK Integer id,
            @Nonnull @Json(failOnUnknown = true) Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void jsonWithFailOnUnknownTrueShouldSucceedWithValidJson() {
        // Exercises the failOnUnknown = true branch in the constructor.
        // Owner id=1 has a valid address, so deserialization should succeed.
        var orm = of(dataSource);
        var result = orm.query("SELECT id, address, telephone FROM owner WHERE id = 1")
                .getSingleResult(OwnerWithFailOnUnknown.class);
        assertNotNull(result.address());
    }

    @Test
    public void jsonWithFailOnUnknownTrueShouldRejectExtraProperties() {
        // JSON with an extra unknown property should cause failure when failOnUnknown is true.
        var orm = of(dataSource);
        var query = orm.query("""
                SELECT id,
                       '{"address":"test","city":"test","extraField":"unexpected"}' AS address,
                       telephone
                FROM owner WHERE id = 1""");
        assertThrows(PersistenceException.class,
                () -> query.getSingleResult(OwnerWithFailOnUnknown.class));
    }

    // failOnMissing enabled

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithFailOnMissing(
            @PK Integer id,
            @Nonnull @Json(failOnMissing = true) Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void jsonWithFailOnMissingTrueShouldSucceedWithCompleteJson() {
        // Exercises the failOnMissing = true branch in the constructor.
        var orm = of(dataSource);
        var result = orm.query("SELECT id, address, telephone FROM owner WHERE id = 1")
                .getSingleResult(OwnerWithFailOnMissing.class);
        assertNotNull(result.address());
    }
}
