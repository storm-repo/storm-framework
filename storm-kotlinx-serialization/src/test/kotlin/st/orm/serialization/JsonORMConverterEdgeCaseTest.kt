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
 * Edge case tests for [st.orm.serialization.spi.JsonORMConverterImpl] covering:
 * - toDatabase with null record values
 * - fromDatabase with null column values
 * - Collection type ref-aware serializer path
 * - Map with Ref keys
 * - Plain (non-Ref) standard serializer fallback
 * - Error path when non-serializable type is used with @Json
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonORMConverterEdgeCaseTest(
    @Autowired val dataSource: DataSource,
) {

    // -- fromDatabase with null value should return null --

    data class NullableAddressHolder(
        @PK val id: Int = 0,
        @Json val address: Address?,
    ) : Entity<Int>

    @Test
    fun `fromDatabase with null JSON column should return null`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, CAST(NULL AS VARCHAR) AS address")
        val result = query.getSingleResult(NullableAddressHolder::class)
        assertNotNull(result)
        assertNull(result.address)
    }

    // -- toDatabase with null value exercises the null record path --

    @DbTable("owner")
    data class OwnerWithNullableAddress(
        @PK val id: Int = 0,
        val firstName: String,
        val lastName: String,
        @Json val address: Address?,
        val telephone: String?,
    ) : Entity<Int>

    @Test
    fun `toDatabase with null address should persist null`() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(OwnerWithNullableAddress::class)
        val owner = OwnerWithNullableAddress(
            firstName = "TestNull",
            lastName = "Address",
            address = null,
            telephone = "5551234",
        )
        val inserted = repository.insertAndFetch(owner)
        assertNull(inserted.address)
    }

    // -- Collection<Ref<Owner>> (not List or Set, uses the else branch in tryCreateRefAwareSerializer) --

    data class OwnerRefCollectionHolder(
        @PK val id: Int = 0,
        @Json val ownerRefs: Collection<Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Collection of Ref should deserialize via tryCreateRefAwareSerializer Collection branch`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[1, 2, 3]' AS owner_refs")
        val result = query.getSingleResult(OwnerRefCollectionHolder::class)
        assertNotNull(result)
        assertEquals(3, result.ownerRefs.size)
    }

    // -- Map<String, Ref<Owner>> with null Ref values --

    data class MapWithNullableRefValueHolder(
        @PK val id: Int = 0,
        @Json val ownerMap: Map<String, Ref<Owner>?>,
    ) : Entity<Int>

    @Test
    fun `Map with nullable Ref values including null should deserialize correctly`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"a\": 1, \"b\": null}' AS owner_map")
        val result = query.getSingleResult(MapWithNullableRefValueHolder::class)
        assertNotNull(result)
        assertEquals(2, result.ownerMap.size)
        assertEquals(1, result.ownerMap["a"]?.id())
    }

    // -- Plain serializable type without Ref (standard serializer fallback in createSerializer) --

    @Serializable
    data class SimpleData(
        val name: String,
        val value: Int,
    )

    data class PlainJsonHolder(
        @PK val id: Int = 0,
        @Json val data: SimpleData,
    ) : Entity<Int>

    @Test
    fun `plain serializable type without Ref should use standard serializer fallback`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"name\": \"test\", \"value\": 42}' AS data")
        val result = query.getSingleResult(PlainJsonHolder::class)
        assertNotNull(result)
        assertEquals("test", result.data.name)
        assertEquals(42, result.data.value)
    }

    // -- Error when non-serializable type is used with @Json (createSerializer error path) --

    data class NonSerializableType(val x: Int)

    data class NonSerializableJsonHolder(
        @PK val id: Int = 0,
        @Json val data: NonSerializableType,
    ) : Entity<Int>

    @Test
    fun `non-serializable type with Json annotation should throw IllegalArgumentException`() {
        val orm = ORMTemplate.of(dataSource)
        assertThrows(PersistenceException::class.java) {
            val query = orm.query("SELECT 1 AS id, '{\"x\": 1}' AS data")
            query.getSingleResult(NonSerializableJsonHolder::class)
        }
    }

    // -- toDatabase serialization for entities with Json field --

    @Test
    fun `toDatabase should serialize Address to JSON string correctly`() {
        val orm = ORMTemplate.of(dataSource)
        val repository = orm.entity(Owner::class)
        val address = Address("456 Oak Lane", "Riverside")
        val owner = Owner(
            firstName = "SerializeTest",
            lastName = "Database",
            address = address,
            telephone = "5559876",
        )
        val inserted = repository.insertAndFetch(owner)
        assertEquals(address, inserted.address)
        assertEquals("SerializeTest", inserted.firstName)
    }

    // -- fromDatabase with SerializationException (invalid JSON) should throw SqlTemplateException --

    data class StrictJsonHolder(
        @PK val id: Int = 0,
        @Json val address: Address,
    ) : Entity<Int>

    @Test
    fun `fromDatabase with invalid JSON should throw PersistenceException wrapping SqlTemplateException`() {
        val orm = ORMTemplate.of(dataSource)
        assertThrows(PersistenceException::class.java) {
            val query = orm.query("SELECT 1 AS id, 'not valid json' AS address")
            query.getSingleResult(StrictJsonHolder::class)
        }
    }

    // -- Map<String, Ref<Owner>> toDatabase round-trip --

    data class RefMapEntityForInsert(
        @PK val id: Int = 0,
        @Json val ownerRefs: Map<String, Ref<Owner>>,
    ) : Entity<Int>

    @Test
    fun `Map with Ref values should round-trip through toDatabase and fromDatabase`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"x\": 5, \"y\": 6}' AS owner_refs")
        val result = query.getSingleResult(RefMapEntityForInsert::class)
        assertNotNull(result)
        assertEquals(5, result.ownerRefs["x"]?.id())
        assertEquals(6, result.ownerRefs["y"]?.id())
    }

    // -- Map without any Ref types (standard Map serializer) --

    data class PlainMapHolder(
        @PK val id: Int = 0,
        @Json val metadata: Map<String, Int>,
    ) : Entity<Int>

    @Test
    fun `Map without Ref types should use standard serializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"a\": 1, \"b\": 2}' AS metadata")
        val result = query.getSingleResult(PlainMapHolder::class)
        assertNotNull(result)
        assertEquals(1, result.metadata["a"])
        assertEquals(2, result.metadata["b"])
    }

    // -- List of plain types (no Ref, standard serializer) --

    data class PlainListHolder(
        @PK val id: Int = 0,
        @Json val names: List<String>,
    ) : Entity<Int>

    @Test
    fun `List of plain types should use standard serializer`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '[\"a\", \"b\", \"c\"]' AS names")
        val result = query.getSingleResult(PlainListHolder::class)
        assertNotNull(result)
        assertEquals(listOf("a", "b", "c"), result.names)
    }
}
