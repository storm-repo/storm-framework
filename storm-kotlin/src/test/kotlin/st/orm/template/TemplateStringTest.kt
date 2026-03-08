package st.orm.template

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class TemplateStringTest(
    @Autowired val orm: ORMTemplate,
) {

    // TemplateString.raw(String) tests

    @Test
    fun `raw from string should create a TemplateString`() {
        val template = TemplateString.raw("SELECT 1")
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `raw from string should produce executable query`() {
        val template = TemplateString.raw("SELECT 1")
        val query = orm.query(template)
        val result = query.singleResult
        result.shouldNotBeNull()
    }

    @Test
    fun `raw from string with full SQL should return correct results`() {
        // Use raw string to select all cities.
        val template = TemplateString.raw("SELECT id, name FROM city ORDER BY id")
        val query = orm.query(template)
        val results = query.resultList
        results shouldHaveSize 6
    }

    @Test
    fun `raw from empty string should create a TemplateString`() {
        val template = TemplateString.raw("")
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    // TemplateString.raw(TemplateBuilder) tests

    @Test
    fun `raw from builder should create a TemplateString`() {
        val template = TemplateString.raw { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `raw from builder should produce executable query returning all cities`() {
        val template = TemplateString.raw { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val results = orm.query(template).getResultList(City::class)
        results shouldHaveSize 6
    }

    @Test
    fun `raw from builder with parameter interpolation should bind values`() {
        val template = TemplateString.raw {
            "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE ${t(Templates.column(st.orm.Metamodel.of<City, String>(City::class.java, "name")))} = ${t("Madison")}"
        }
        val results = orm.query(template).getResultList(City::class)
        results shouldHaveSize 1
        results[0].name shouldBe "Madison"
    }

    // TemplateString.wrap() tests

    @Test
    fun `wrap with value should create a TemplateString`() {
        val template = TemplateString.wrap(42)
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `wrap with null value should create a TemplateString`() {
        val template = TemplateString.wrap(null)
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `wrap with entity class should create usable TemplateString`() {
        // Wrapping a KClass is used for select clauses.
        val template = TemplateString.wrap(City::class)
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    // TemplateString.combine() tests

    @Test
    fun `combine should merge multiple template strings`() {
        val part1 = TemplateString.raw("SELECT id, name FROM city WHERE id = ")
        val part2 = TemplateString.wrap(1)
        val combined = TemplateString.combine(part1, part2)
        combined.shouldNotBeNull()
        combined.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `combine should produce executable query`() {
        val selectPart = TemplateString.raw("SELECT id, name FROM city WHERE id = ")
        val paramPart = TemplateString.wrap(1)
        val combined = TemplateString.combine(selectPart, paramPart)
        val query = orm.query(combined)
        val result = query.getResultList(City::class)
        result shouldHaveSize 1
        result[0].id shouldBe 1
        result[0].name shouldBe "Sun Paririe"
    }

    @Test
    fun `combine with three parts should produce correct query`() {
        val prefix = TemplateString.raw("SELECT id, name FROM city WHERE name = ")
        val value = TemplateString.wrap("Madison")
        val suffix = TemplateString.raw(" ORDER BY id")
        val combined = TemplateString.combine(prefix, value, suffix)
        val results = orm.query(combined).getResultList(City::class)
        results shouldHaveSize 1
        results[0].name shouldBe "Madison"
    }

    @Test
    fun `combine with single template should return equivalent template`() {
        val original = TemplateString.raw("SELECT 1")
        val combined = TemplateString.combine(original)
        combined.shouldNotBeNull()
        // Should still produce the same result.
        val result = orm.query(combined).singleResult
        result.shouldNotBeNull()
    }

    // TemplateBuilder.build() extension tests

    @Test
    fun `TemplateBuilder build should create TemplateString`() {
        val builder: TemplateBuilder = { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val template = builder.build()
        template.shouldNotBeNull()
        template.shouldBeInstanceOf<TemplateString>()
    }

    @Test
    fun `TemplateBuilder build should produce query returning all cities`() {
        val builder: TemplateBuilder = { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val results = orm.query(builder.build()).getResultList(City::class)
        results shouldHaveSize 6
    }

    // Integration: TemplateString with orm.query(TemplateBuilder)

    @Test
    fun `query with TemplateBuilder lambda should return results`() {
        val results = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }.getResultList(City::class)
        results shouldHaveSize 6
    }

    @Test
    fun `query with TemplateBuilder using parameter should filter results`() {
        val results = orm.query {
            "SELECT ${t(City::class)} FROM ${t(City::class)} WHERE ${t(Templates.column(st.orm.Metamodel.of<City, String>(City::class.java, "name")))} = ${t("Monona")}"
        }.getResultList(City::class)
        results shouldHaveSize 1
        results[0].name shouldBe "Monona"
    }

    @Test
    fun `query with TemplateString should return results`() {
        val template = TemplateString.raw { "SELECT ${t(City::class)} FROM ${t(City::class)}" }
        val results = orm.query(template).getResultList(City::class)
        results shouldHaveSize 6
    }

    // TemplateContext.t and TemplateContext.interpolate tests

    @Test
    fun `TemplateContext t and interpolate should produce same results`() {
        // Both t() and interpolate() should behave identically.
        val resultsViaT = orm.query { "SELECT ${t(City::class)} FROM ${t(City::class)}" }.getResultList(City::class)
        val resultsViaInterpolate = orm.query { "SELECT ${interpolate(City::class)} FROM ${interpolate(City::class)}" }.getResultList(City::class)
        resultsViaT shouldHaveSize 6
        resultsViaInterpolate shouldHaveSize 6
        resultsViaT.map { it.id }.sorted() shouldBe resultsViaInterpolate.map { it.id }.sorted()
    }
}
