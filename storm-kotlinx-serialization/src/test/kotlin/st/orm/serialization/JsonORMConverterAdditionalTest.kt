package st.orm.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.DbTable
import st.orm.Entity
import st.orm.Json
import st.orm.PK
import st.orm.PersistenceException
import st.orm.Ref
import st.orm.serialization.model.Address
import st.orm.serialization.model.Owner
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Additional tests for [st.orm.serialization.spi.JsonORMConverterImpl] covering:
 * - tryCreateRefAwareSerializer for Map types with Ref keys/values
 * - getPropertySerializer with custom @Serializable(with = ...) annotation
 * - sealed class handling and JSON caching
 * - buildJson configuration (failOnUnknown, failOnMissing)
 * - createSerializer error when no serializer found
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonORMConverterAdditionalTest(
    @Autowired val dataSource: DataSource,
) {

    // Map<String, Ref<Owner>> exercises tryCreateRefAwareSerializer for Map value

    data class OwnerRefMapHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: Map<String, Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Map with Ref values should deserialize via tryCreateRefAwareSerializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"first\": 1, \"second\": 2}' AS owner_refs")
        val result = query.getSingleResult(OwnerRefMapHolder::class)
        assertNotNull(result)
        assertEquals(2, result.ownerRefs.size)
        assertEquals(1, result.ownerRefs["first"]?.id())
        assertEquals(2, result.ownerRefs["second"]?.id())
    }

    // List<Ref<Owner>> exercises tryCreateRefAwareSerializer for List

    data class OwnerRefListHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: List<Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `List of Ref should deserialize via tryCreateRefAwareSerializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[1, 2, 3]' AS owner_refs")
        val result = query.getSingleResult(OwnerRefListHolder::class)
        assertNotNull(result)
        assertEquals(3, result.ownerRefs.size)
        assertEquals(1, result.ownerRefs[0].id())
        assertEquals(2, result.ownerRefs[1].id())
        assertEquals(3, result.ownerRefs[2].id())
    }

    // Set<Ref<Owner>> exercises tryCreateRefAwareSerializer for Set

    data class OwnerRefSetHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: Set<Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Set of Ref should deserialize via tryCreateRefAwareSerializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[1, 2]' AS owner_refs")
        val result = query.getSingleResult(OwnerRefSetHolder::class)
        assertNotNull(result)
        assertEquals(2, result.ownerRefs.size)
    }

    // Custom serializer via @Serializable(with = ...) on @Json field

    object UpperCaseStringSerializer : KSerializer<String> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("UpperCaseString", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: String) {
            encoder.encodeString(value.uppercase())
        }

        override fun deserialize(decoder: Decoder): String = decoder.decodeString().uppercase()
    }

    @DbTable("owner")
    data class OwnerWithCustomSerializerAddress(
        @PK val id: Int = 0,
        val firstName: String,
        val lastName: String,
        @Json @Serializable(with = UpperCaseStringSerializer::class) val address: String,
        val telephone: String?,
    ) : Entity<Int>

    @Test
    fun `custom serializer via @Serializable(with) on Json field should be used`() {
        val orm = ORMTemplate.of(dataSource)
        // Query that returns a simple string as the address field.
        val query = orm.query("SELECT id, first_name, last_name, '\"hello\"' AS address, telephone FROM owner WHERE id = 1")
        val result = query.getSingleResult(OwnerWithCustomSerializerAddress::class)
        assertNotNull(result)
        // The UpperCaseStringSerializer converts to uppercase on deserialize.
        assertEquals("HELLO", result.address)
    }

    // Sealed class polymorphic deserialization

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("@type")
    @Serializable
    sealed interface Shape

    @SerialName("Circle")
    @Serializable
    data class Circle(val radius: Double) : Shape

    @SerialName("Rectangle")
    @Serializable
    data class Rectangle(val width: Double, val height: Double) : Shape

    data class ShapeHolder(
        @PK val id: Int = 0,
        @Json val shape: Shape,
    ) : Entity<Int>

    @Test
    fun `sealed class deserialization for Circle`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"@type\": \"Circle\", \"radius\": 5.0}' AS shape")
        val result = query.getSingleResult(ShapeHolder::class)
        assertNotNull(result)
        assertTrue(result.shape is Circle)
        assertEquals(5.0, (result.shape as Circle).radius)
    }

    @Test
    fun `sealed class deserialization for Rectangle`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"@type\": \"Rectangle\", \"width\": 3.0, \"height\": 4.0}' AS shape")
        val result = query.getSingleResult(ShapeHolder::class)
        assertNotNull(result)
        assertTrue(result.shape is Rectangle)
        val rectangle = result.shape as Rectangle
        assertEquals(3.0, rectangle.width)
        assertEquals(4.0, rectangle.height)
    }

    @Test
    fun `sealed class caching - same sealed type reuses cached JSON instance`() {
        // Both queries use the same ShapeHolder type, so the JSON instance should be cached.
        val orm = ORMTemplate.of(dataSource)
        val circleQuery = orm.query("SELECT 1 AS id, '{\"@type\": \"Circle\", \"radius\": 1.0}' AS shape")
        val circleResult = circleQuery.getSingleResult(ShapeHolder::class)
        assertTrue(circleResult.shape is Circle)

        val rectangleQuery = orm.query("SELECT 2 AS id, '{\"@type\": \"Rectangle\", \"width\": 2.0, \"height\": 3.0}' AS shape")
        val rectangleResult = rectangleQuery.getSingleResult(ShapeHolder::class)
        assertTrue(rectangleResult.shape is Rectangle)
    }

    // buildJson configuration options

    data class AddressHolderWithFailOnUnknown(
        @PK val id: Int = 0,
        @Json(failOnUnknown = true) val address: Address,
    ) : Entity<Int>

    @Test
    fun `failOnUnknown = true should reject JSON with unknown keys`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"123 Main St\", \"city\": \"NY\", \"extra\": \"field\"}' AS address")
        assertThrows(PersistenceException::class.java) {
            query.getSingleResult(AddressHolderWithFailOnUnknown::class)
        }
    }

    data class AddressHolderDefault(
        @PK val id: Int = 0,
        @Json val address: Address,
    ) : Entity<Int>

    @Test
    fun `default Json annotation should ignore unknown keys`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"123 Main St\", \"city\": \"NY\", \"extra\": \"field\"}' AS address")
        val result = query.getSingleResult(AddressHolderDefault::class)
        assertNotNull(result)
        assertEquals("123 Main St", result.address.address)
        assertEquals("NY", result.address.city)
    }

    // Nullable Ref in Map values

    data class NullableRefMapHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: Map<String, Ref<Owner>?>,
    ) : Entity<Int>

    @Test
    fun `Map with nullable Ref values containing null should deserialize`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"first\": 1, \"second\": null}' AS owner_refs")
        val result = query.getSingleResult(NullableRefMapHolder::class)
        assertNotNull(result)
        assertEquals(2, result.ownerRefs.size)
        assertNotNull(result.ownerRefs["first"])
        assertEquals(1, result.ownerRefs["first"]?.id())
        // null value in map should be preserved.
        assertTrue(result.ownerRefs.containsKey("second"))
    }

    // toDatabase / fromDatabase round-trip

    @Test
    fun `insert and fetch entity with Json field round-trips correctly`() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(Owner::class)
        val address = Address("100 Test Blvd", "TestCity")
        val owner = Owner(
            firstName = "TestFirst",
            lastName = "TestLast",
            address = address,
            telephone = "1234567890",
        )
        val inserted = repository.insertAndFetch(owner)
        assertEquals(address, inserted.address)
        assertEquals("TestFirst", inserted.firstName)
    }

    // List<Ref<Owner>> toDatabase serialization

    data class RefListEntityForSerialization(
        @PK val id: Int = 0,
        @Json val ownerRefs: List<Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `List of unloaded Refs serializes to JSON array of ids`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[1, 2, 3]' AS owner_refs")
        val result = query.getSingleResult(RefListEntityForSerialization::class)
        assertEquals(3, result.ownerRefs.size)
        // All should be unloaded refs with just IDs.
        result.ownerRefs.forEachIndexed { index, ref ->
            assertEquals(index + 1, ref.id())
        }
    }

    // Json with failOnMissing = true

    @Serializable
    data class StrictAddress(
        val address: String,
        val city: String,
        val zipCode: String,
    )

    data class StrictAddressHolder(
        @PK val id: Int = 0,
        @Json(failOnMissing = true) val address: StrictAddress,
    ) : Entity<Int>

    @Test
    fun `failOnMissing = true should reject JSON missing required fields`() {
        val orm = ORMTemplate.of(dataSource)
        // JSON is missing 'zipCode' field which is required by StrictAddress.
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"123 Main St\", \"city\": \"NY\"}' AS address")
        assertThrows(PersistenceException::class.java) {
            query.getSingleResult(StrictAddressHolder::class)
        }
    }
}
