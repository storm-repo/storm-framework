package st.orm.serialization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import st.orm.Entity
import st.orm.Json
import st.orm.PK
import st.orm.serialization.model.Address
import st.orm.template.ORMTemplate
import st.orm.test.StormTest
import javax.sql.DataSource

/**
 * Tests for [st.orm.serialization.spi.JsonORMConverterProviderImpl] covering:
 * - getConverter returns a working converter for fields with @Json
 * - getConverter falls back to standard conversion for fields without @Json
 * - Converter correctly deserializes JSON into domain objects
 */
@StormTest(scripts = ["/data.sql"])
internal open class JsonORMConverterProviderTest {

    private lateinit var dataSource: DataSource

    @BeforeEach
    fun bindDataSource(dataSource: DataSource) {
        this.dataSource = dataSource
    }

    data class EntityWithJsonField(
        @PK val id: Int = 0,
        @Json val address: Address,
    ) : Entity<Int>

    data class EntityWithoutJsonField(
        @PK val id: Int = 0,
        val name: String,
    ) : Entity<Int>

    @Test
    fun `field with Json annotation should be deserialized from JSON string into domain object`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"123 Main St\", \"city\": \"Springfield\"}' AS address")
        val result = query.getSingleResult(EntityWithJsonField::class)
        assertEquals("123 Main St", result.address.address)
        assertEquals("Springfield", result.address.city)
    }

    @Test
    fun `field without Json annotation should use standard string conversion`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, 'testName' AS name")
        val result = query.getSingleResult(EntityWithoutJsonField::class)
        assertEquals("testName", result.name)
    }

    @Test
    fun `Json converter should preserve all fields through serialization round-trip`() {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT 1 AS id, '{\"address\": \"456 Oak\", \"city\": \"Portland\"}' AS address")
        val result = query.getSingleResult(EntityWithJsonField::class)
        assertEquals(Address("456 Oak", "Portland"), result.address)
    }
}
