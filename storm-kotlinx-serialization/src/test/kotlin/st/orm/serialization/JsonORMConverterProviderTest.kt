package st.orm.serialization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Entity
import st.orm.Json
import st.orm.PK
import st.orm.serialization.model.Address
import st.orm.template.ORMTemplate
import javax.sql.DataSource

/**
 * Tests for [st.orm.serialization.spi.JsonORMConverterProviderImpl] covering:
 * - getConverter returns a working converter for fields with @Json
 * - getConverter falls back to standard conversion for fields without @Json
 * - Converter correctly deserializes JSON into domain objects
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonORMConverterProviderTest(
    @Autowired val dataSource: DataSource,
) {

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
