package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.template.Templates.unsafe
import st.orm.template.model.City
import st.orm.template.model.Owner

/**
 * Integration tests verifying the Storm Template compiler plugin.
 *
 * These tests exercise the plugin's auto-wrapping of string interpolations inside
 * TemplateBuilder lambdas. The compiler plugin is applied during test compilation
 * via the kotlin-maven-plugin dependency on storm-compiler-plugin.
 *
 * Without the plugin, these tests would require explicit `t()` wrapping:
 *   orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = ${t(1)}" }
 *
 * With the plugin, the `t()` calls are inserted automatically:
 *   orm.query { "SELECT ${City::class} FROM ${City::class} WHERE id = ${1}" }
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class CompilerPluginTest(
    @Autowired val orm: ORMTemplate,
) {

    @Test
    fun `query with auto-wrapped class interpolation should return typed result`() {
        val city = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE id = ${1}" }
            .getSingleResult(City::class)
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query with auto-wrapped variable should return typed result`() {
        val cityId = 1
        val city = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE id = $cityId" }
            .getSingleResult(City::class)
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query with auto-wrapped optional result should return null for no match`() {
        val nonExistentId = 9999
        val city = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE id = $nonExistentId" }
            .getOptionalResult(City::class)
        city.shouldBeNull()
    }

    @Test
    fun `query with auto-wrapped result list should return all matches`() {
        val cities = orm.query { "SELECT ${City::class} FROM ${City::class}" }
            .getResultList(City::class)
        cities shouldHaveSize 6
    }

    @Test
    fun `query with auto-wrapped result flow should return all matches`(): Unit = runBlocking {
        val count = orm.query { "SELECT ${City::class} FROM ${City::class}" }
            .getResultFlow(City::class)
            .count()
        count shouldBe 6
    }

    @Test
    fun `query with multiple auto-wrapped interpolations`() {
        val minId = 1
        val maxId = 3
        val cities = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE id >= $minId AND id <= $maxId" }
            .getResultList(City::class)
        cities shouldHaveSize 3
    }

    @Test
    fun `query with auto-wrapped string value`() {
        val cityName = "Madison"
        val city = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE name = $cityName" }
            .getSingleResult(City::class)
        city.id shouldBe 2
    }

    @Test
    fun `mixed explicit t() and auto-wrapped interpolations`() {
        val cityId = 1
        val city = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE id = $cityId" }
            .getSingleResult(City::class)
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `query with multiline auto-wrapped template`() {
        val cityId = 2
        val city = orm.query {
            """
            SELECT ${City::class}
            FROM ${City::class}
            WHERE id = $cityId
            """.trimIndent()
        }.getSingleResult(City::class)
        city.name shouldBe "Madison"
    }

    @Test
    fun `plain literal template is unchanged`() {
        val result = orm.query { "SELECT COUNT(*) FROM city" }.singleResult
        result shouldBe arrayOf(6L)
    }

    @Test
    fun `owner query with auto-wrapped interpolation`() {
        val firstName = "George"
        val owners = orm.query { "SELECT ${Owner::class} FROM ${Owner::class} WHERE first_name = $firstName" }
            .getResultList(Owner::class)
        owners shouldHaveSize 1
        owners[0].lastName shouldBe "Franklin"
    }

    @Test
    fun `unsafe element is auto-wrapped in t() and inlined as raw SQL`() {
        val cities = orm.query { "SELECT ${City::class} FROM ${City::class} WHERE ${unsafe("name = 'Madison'")}" }
            .getResultList(City::class)
        cities shouldHaveSize 1
        cities[0].id shouldBe 2
    }
}
