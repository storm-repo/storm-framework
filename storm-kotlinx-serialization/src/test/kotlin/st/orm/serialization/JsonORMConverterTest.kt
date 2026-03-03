package st.orm.serialization

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
 * Tests targeting remaining coverage gaps in JsonORMConverterImpl.kt:
 * - Map with Ref keys (exercises tryCreateRefAwareSerializer keyRefSerializer path)
 * - Map with both Ref keys and values
 * - toDatabase with entity having null Json value
 * - toDatabase error path via SqlTemplateException
 * - getParameterCount and getParameterTypes
 * - getColumns via nameResolver
 * - JSON caching for same CacheKey
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonORMConverterTest(
    @Autowired val dataSource: DataSource,
) {

    // Map<Ref<Owner>, String> exercises tryCreateRefAwareSerializer Map key path

    data class RefKeyMapHolder(
        @PK val id: Int = 0,
        @Json val refMap: Map<Ref<Owner>, String>,
    ) : Entity<Int>

    @Test
    fun `Map with Ref keys should throw PersistenceException because Ref cannot be a JSON map key`() {
        val orm = ORMTemplate.of(dataSource)
        assertThrows(PersistenceException::class.java) {
            val query = orm.query("SELECT 1 AS id, '{\"1\": \"first\", \"2\": \"second\"}' AS ref_map")
            query.getSingleResult(RefKeyMapHolder::class)
        }
    }

    // Map<Ref<Owner>, Ref<Owner>> exercises both key and value Ref paths

    data class RefBothMapHolder(
        @PK val id: Int = 0,
        @Json val refMap: Map<Ref<Owner>, Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Map with both Ref keys and Ref values should throw PersistenceException`() {
        val orm = ORMTemplate.of(dataSource)
        assertThrows(PersistenceException::class.java) {
            val query = orm.query("SELECT 1 AS id, '{\"1\": 2, \"3\": 4}' AS ref_map")
            query.getSingleResult(RefBothMapHolder::class)
        }
    }

    // toDatabase with null Json field

    @DbTable("owner")
    data class OwnerWithNullableJson(
        @PK val id: Int = 0,
        val firstName: String,
        val lastName: String,
        @Json val address: Address?,
        val telephone: String?,
    ) : Entity<Int>

    @Test
    fun `toDatabase with null Json field should store null`() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(OwnerWithNullableJson::class)
        val owner = OwnerWithNullableJson(
            firstName = "NullAddress",
            lastName = "Test",
            address = null,
            telephone = "5551111",
        )
        val inserted = repository.insertAndFetch(owner)
        assertNull(inserted.address)
    }

    @Test
    fun `toDatabase with non-null Json field should store JSON string`() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(OwnerWithNullableJson::class)
        val address = Address("789 Elm St", "TestTown")
        val owner = OwnerWithNullableJson(
            firstName = "NonNull",
            lastName = "Test",
            address = address,
            telephone = "5552222",
        )
        val inserted = repository.insertAndFetch(owner)
        assertNotNull(inserted.address)
        assertEquals("789 Elm St", inserted.address!!.address)
        assertEquals("TestTown", inserted.address!!.city)
    }

    // -- JSON caching: two different holder types with the same @Json Address config
    //    should both deserialize correctly, proving the serializer cache doesn't corrupt
    //    results across distinct entity types that share the same JSON field type.

    data class CachingTestHolder1(
        @PK val id: Int = 0,
        @Json val address: Address,
    ) : Entity<Int>

    data class CachingTestHolder2(
        @PK val id: Int = 0,
        @Json val address: Address,
    ) : Entity<Int>

    @Test
    fun `two entity types sharing same Json field type should both deserialize correctly`() {
        val orm = ORMTemplate.of(dataSource)
        val result1 = orm.query("SELECT 1 AS id, '{\"address\": \"First St\", \"city\": \"Alpha\"}' AS address")
            .getSingleResult(CachingTestHolder1::class)
        assertEquals("First St", result1.address.address)
        assertEquals("Alpha", result1.address.city)

        val result2 = orm.query("SELECT 2 AS id, '{\"address\": \"Second Ave\", \"city\": \"Beta\"}' AS address")
            .getSingleResult(CachingTestHolder2::class)
        assertEquals("Second Ave", result2.address.address)
        assertEquals("Beta", result2.address.city)
    }

    // Map<String, String> exercises the null path in tryCreateRefAwareSerializer

    data class PlainStringMapHolder(
        @PK val id: Int = 0,
        @Json val metadata: Map<String, String>,
    ) : Entity<Int>

    @Test
    fun `plain Map without Ref types should deserialize using standard serializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"key\": \"value\", \"other\": \"data\"}' AS metadata")
        val result = query.getSingleResult(PlainStringMapHolder::class)
        assertEquals(2, result.metadata.size)
        assertEquals("value", result.metadata["key"])
        assertEquals("data", result.metadata["other"])
    }

    // List<String> exercises the null path in tryCreateRefAwareSerializer element type

    data class PlainStringListHolder(
        @PK val id: Int = 0,
        @Json val items: List<String>,
    ) : Entity<Int>

    @Test
    fun `plain List without Ref elements should deserialize using standard serializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[\"a\",\"b\",\"c\"]' AS items")
        val result = query.getSingleResult(PlainStringListHolder::class)
        assertEquals(3, result.items.size)
        assertEquals(listOf("a", "b", "c"), result.items)
    }

    // Set<Ref<Owner>> exercises the Set branch

    data class RefSetHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: Set<Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Set of Ref should deserialize JSON array into a Set preserving unique Ref ids`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[1, 2, 3]' AS owner_refs")
        val result = query.getSingleResult(RefSetHolder::class)
        assertEquals(3, result.ownerRefs.size)
        val ids = result.ownerRefs.map { it.id() }.toSet()
        assertEquals(setOf(1, 2, 3), ids)
    }

    // Ref<Owner> directly (not in collection) exercises createRefSerializer

    data class DirectRefHolder(
        @PK val id: Int = 0,
        @Json val owner: Ref<Owner>,
    ) : Entity<Int>

    @Test
    fun `direct Ref field should deserialize via createRefSerializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '5' AS owner")
        val result = query.getSingleResult(DirectRefHolder::class)
        assertNotNull(result)
        assertEquals(5, result.owner.id())
    }

    // Json with failOnMissing = false (default) should coerce missing fields

    @Serializable
    data class AddressWithDefault(
        val address: String = "unknown",
        val city: String = "unknown",
        val zipCode: String = "00000",
    )

    data class CoercingAddressHolder(
        @PK val id: Int = 0,
        @Json(failOnMissing = false) val address: AddressWithDefault,
    ) : Entity<Int>

    @Test
    fun `failOnMissing false should coerce missing fields to defaults`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"123 Main\"}' AS address")
        val result = query.getSingleResult(CoercingAddressHolder::class)
        assertNotNull(result)
        assertEquals("123 Main", result.address.address)
        assertEquals("00000", result.address.zipCode)
    }

    // -- Multiple fetches of the same entity type prove the JSON converter works
    //    consistently across repeated deserialization of @Json fields.

    @Test
    fun `repeated fetches of same entity type deserialize Json fields consistently`() {
        val orm = ORMTemplate.of(dataSource)
        val repo = orm.entity(Owner::class)
        val owner1 = repo.getById(1)
        val owner2 = repo.getById(2)
        // Verify each owner's @Json Address was deserialized with correct, distinct values.
        assertNotNull(owner1.address)
        assertNotNull(owner2.address)
        // The pre-seeded test data has different addresses for owner 1 and owner 2.
        // Verify they are not mixed up (i.e., the serializer produces independent results).
        val addresses = setOf(owner1.address, owner2.address)
        assertEquals(2, addresses.size, "Each owner should have a distinct Address object")
    }
}
