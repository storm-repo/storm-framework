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
import java.util.List;
import java.util.Map;
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
import st.orm.Ref;
import st.orm.jackson.model.Address;
import st.orm.jackson.model.Owner;

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

    // Nested deserialization: a custom deserializer that issues a query mid-deserialization.

    public static class NestedQueryMarkerDeserializer extends JsonDeserializer<String> {
        static DataSource nestedDataSource;

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context)
                throws java.io.IOException {
            String text = parser.getText();
            // Maps Owner, whose @Json address field enters fromDatabase re-entrantly on this thread.
            of(nestedDataSource)
                    .query("SELECT id, first_name, last_name, address, telephone FROM owner WHERE id = 2")
                    .getSingleResult(Owner.class);
            return text;
        }
    }

    public record OwnerSnapshot(
            @JsonDeserialize(using = NestedQueryMarkerDeserializer.class) String marker,
            Ref<Owner> owner
    ) {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithSnapshot(
            @PK Integer id,
            @Nonnull @Json OwnerSnapshot snapshot,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void refDeserializedAfterNestedConversionShouldRemainAttached() {
        // The marker field deserializes first and issues a nested query, which binds and unbinds the nested
        // conversion's RefFactory. The owner field that follows must still see the outer factory: the
        // resulting ref is attached and fetches the owner.
        NestedQueryMarkerDeserializer.nestedDataSource = dataSource;
        var orm = of(dataSource);
        var query = orm.query("SELECT id, JSON_OBJECT('marker' VALUE 'audit', 'owner' VALUE id) AS snapshot, telephone FROM owner WHERE id = 1");
        var result = query.getSingleResult(OwnerWithSnapshot.class);
        var ownerRef = result.snapshot().owner();
        assertTrue(ownerRef.isFetchable());
        assertEquals("Betty", ownerRef.fetch().firstName());
    }

    // Two @Json fields of different types sharing one custom serializer class.

    public static class TypeNameMarkerSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
                throws java.io.IOException {
            gen.writeString("marker:" + value.getClass().getSimpleName());
        }
    }

    public record Phone(String number) {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithSharedSerializer(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json @JsonSerialize(using = TypeNameMarkerSerializer.class) Address address,
            @Nonnull @Json @JsonSerialize(using = TypeNameMarkerSerializer.class) Phone telephone
    ) implements Entity<Integer> {}

    public record RawOwnerRow(String address, String telephone) {}

    @Test
    public void fieldsOfDifferentTypesSharingSerializerClassShouldEachUseTheSerializer() {
        // Both fields use the same serializer class but have different types, so each field needs a mapper with
        // the serializer registered for its own type.
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithSharedSerializer.class);
        var owner = OwnerWithSharedSerializer.builder()
                .firstName("Shared")
                .lastName("Serializer")
                .address(new Address("1 Way", "Town"))
                .telephone(new Phone("555"))
                .build();
        repository.insert(owner);
        var row = orm.query("SELECT address, telephone FROM owner WHERE first_name = 'Shared'")
                .getSingleResult(RawOwnerRow.class);
        assertEquals("\"marker:Address\"", row.address());
        assertEquals("\"marker:Phone\"", row.telephone());
    }

    // Sealed element type inside a container.

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithPolymorphicPersonList(
            @PK Integer id,
            @Nonnull @Json List<PolymorphicPerson> address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void polymorphicJsonListShouldResolveSubtypesViaDiscriminator() {
        // The sealed interface appears as the List element type; its permitted subtypes are registered from the
        // generic type, not just the raw List type.
        var orm = of(dataSource);
        var query = orm.query("""
                SELECT id,
                       '[{"@type":"PersonA","firstName":"Jane","lastName":"Doe"},{"@type":"PersonB","firstName":"John","lastName":"Doe"}]' AS address,
                       telephone
                FROM owner WHERE id = 1""");
        var result = query.getSingleResult(OwnerWithPolymorphicPersonList.class);
        assertEquals(2, result.address().size());
        assertTrue(result.address().get(0) instanceof PersonA);
        assertTrue(result.address().get(1) instanceof PersonB);
    }

    @Test
    public void polymorphicJsonListShouldRoundTripThroughDatabase() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithPolymorphicPersonList.class);
        var owner = OwnerWithPolymorphicPersonList.builder()
                .address(List.of(new PersonA("Jane", "Doe"), new PersonB("John", "Doe")))
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertEquals(List.of(new PersonA("Jane", "Doe"), new PersonB("John", "Doe")), inserted.address());
    }

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithPolymorphicAddress(
            @PK Integer id,
            @Nonnull @Json PolymorphicPerson address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void polymorphicJsonFieldShouldRoundTripThroughDatabase() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithPolymorphicAddress.class);
        var owner = OwnerWithPolymorphicAddress.builder()
                .address(new PersonB("Jane", "Doe"))
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertEquals(new PersonB("Jane", "Doe"), inserted.address());
    }

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithPolymorphicPersonMap(
            @PK Integer id,
            @Nonnull @Json Map<String, PolymorphicPerson> address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void polymorphicJsonMapValuesShouldRoundTripThroughDatabase() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithPolymorphicPersonMap.class);
        var persons = Map.of(
                "primary", (PolymorphicPerson) new PersonA("Jane", "Doe"),
                "secondary", new PersonB("John", "Doe"));
        var owner = OwnerWithPolymorphicPersonMap.builder()
                .address(persons)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertEquals(persons, inserted.address());
    }
}
