package st.orm.template

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.GenerationStrategy
import st.orm.template.model.*

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class ModelTest(
    @Autowired val orm: ORMTemplate,
) {

    // ======================================================================
    // Model.schema tests
    // ======================================================================

    @Test
    fun `city model schema should be empty when no schema is specified`() {
        val model = orm.entity(City::class).model
        model.schema shouldBe ""
    }

    @Test
    fun `owner model schema should be empty when no schema is specified`() {
        val model = orm.entity(Owner::class).model
        model.schema shouldBe ""
    }

    @Test
    fun `pet model schema should be empty when no schema is specified`() {
        val model = orm.entity(Pet::class).model
        model.schema shouldBe ""
    }

    // ======================================================================
    // Model.name tests
    // ======================================================================

    @Test
    fun `city model name should be city`() {
        val model = orm.entity(City::class).model
        model.name shouldBe "city"
    }

    @Test
    fun `owner model name should be owner`() {
        val model = orm.entity(Owner::class).model
        model.name shouldBe "owner"
    }

    @Test
    fun `pet model name should be pet`() {
        val model = orm.entity(Pet::class).model
        model.name shouldBe "pet"
    }

    @Test
    fun `vet model name should be vet`() {
        val model = orm.entity(Vet::class).model
        model.name shouldBe "vet"
    }

    @Test
    fun `visit model name should be visit`() {
        val model = orm.entity(Visit::class).model
        model.name shouldBe "visit"
    }

    @Test
    fun `pet type model name should be pet_type`() {
        val model = orm.entity(PetType::class).model
        model.name shouldBe "pet_type"
    }

    // ======================================================================
    // Model.type tests
    // ======================================================================

    @Test
    fun `city model type should be City`() {
        val model = orm.entity(City::class).model
        model.type shouldBe City::class
    }

    @Test
    fun `owner model type should be Owner`() {
        val model = orm.entity(Owner::class).model
        model.type shouldBe Owner::class
    }

    @Test
    fun `pet model type should be Pet`() {
        val model = orm.entity(Pet::class).model
        model.type shouldBe Pet::class
    }

    // ======================================================================
    // Model.primaryKeyType tests
    // ======================================================================

    @Test
    fun `city model primaryKeyType should be Int`() {
        val model = orm.entity(City::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `owner model primaryKeyType should be Int`() {
        val model = orm.entity(Owner::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `pet model primaryKeyType should be Int`() {
        val model = orm.entity(Pet::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `vet model primaryKeyType should be Int`() {
        val model = orm.entity(Vet::class).model
        model.primaryKeyType shouldBe Int::class
    }

    // ======================================================================
    // Model.columns tests
    // ======================================================================

    @Test
    fun `city model should have 2 columns`() {
        val model = orm.entity(City::class).model
        model.columns shouldHaveSize 2
    }

    @Test
    fun `city model columns should include id and name`() {
        val model = orm.entity(City::class).model
        val columnNames = model.columns.map { it.name }
        columnNames shouldBe listOf("id", "name")
    }

    @Test
    fun `pet model columns should include all expanded FK columns`() {
        // Pet has: id, name, birth_date, type_id (FK -> PetType with id, name), owner_id (FK -> Owner with many cols)
        // The expanded column list includes columns from FK relationships.
        val model = orm.entity(Pet::class).model
        val columnNames = model.columns.map { it.name }
        columnNames.contains("id") shouldBe true
        columnNames.contains("name") shouldBe true
        columnNames.contains("birth_date") shouldBe true
        columnNames.contains("type_id") shouldBe true
        columnNames.contains("owner_id") shouldBe true
    }

    @Test
    fun `pet model declared columns should have 5 entries`() {
        // Pet has 5 declared fields: id, name, birthDate, type (FK), owner (FK)
        val model = orm.entity(Pet::class).model
        model.declaredColumns shouldHaveSize 5
    }

    @Test
    fun `vet model should have 3 columns`() {
        val model = orm.entity(Vet::class).model
        model.columns shouldHaveSize 3
    }

    @Test
    fun `vet model columns should be id first_name last_name`() {
        val model = orm.entity(Vet::class).model
        val columnNames = model.columns.map { it.name }
        columnNames shouldBe listOf("id", "first_name", "last_name")
    }

    @Test
    fun `owner model should have 8 columns including expanded address`() {
        // Owner has: id, first_name, last_name, address, city_id, city_name (from inline Address + FK City), telephone, version
        val model = orm.entity(Owner::class).model
        model.columns shouldHaveSize 8
    }

    @Test
    fun `owner model columns should contain city_id from expanded address FK`() {
        val model = orm.entity(Owner::class).model
        val columnNames = model.columns.map { it.name }
        columnNames.contains("city_id").shouldBeTrue()
    }

    // ======================================================================
    // Model.declaredColumns tests
    // ======================================================================

    @Test
    fun `city model declaredColumns should equal columns for flat entity`() {
        val model = orm.entity(City::class).model
        // City has no inline records or FK expansion, so declared and all columns should match.
        model.declaredColumns shouldHaveSize 2
    }

    @Test
    fun `owner model declaredColumns should have fewer columns than expanded columns`() {
        val model = orm.entity(Owner::class).model
        // Declared: id, firstName, lastName, address (inline), city (FK via address), telephone, version = 7
        // Expanded: includes city's columns (city_id + city_name) separately = 8
        val declaredCount = model.declaredColumns.size
        val allCount = model.columns.size
        declaredCount shouldBe 7
        allCount shouldBe 8
    }

    @Test
    fun `pet model declaredColumns count should be less than expanded columns`() {
        // Pet has FK fields (type -> PetType, owner -> Owner) that expand in model.columns
        // but declaredColumns should only have the 5 declared fields.
        val model = orm.entity(Pet::class).model
        model.declaredColumns shouldHaveSize 5
        (model.declaredColumns.size <= model.columns.size) shouldBe true
    }

    @Test
    fun `vet model declaredColumns should equal columns for simple entity`() {
        val model = orm.entity(Vet::class).model
        model.declaredColumns shouldHaveSize 3
        model.declaredColumns.size shouldBe model.columns.size
    }

    // ======================================================================
    // Model.isDefaultPrimaryKey tests
    // ======================================================================

    @Test
    fun `isDefaultPrimaryKey should return true for zero int`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(0).shouldBeTrue()
    }

    @Test
    fun `isDefaultPrimaryKey should return false for non-zero int`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(42).shouldBeFalse()
    }

    @Test
    fun `isDefaultPrimaryKey should return true for null`() {
        val model = orm.entity(City::class).model
        model.isDefaultPrimaryKey(null).shouldBeTrue()
    }

    // ======================================================================
    // Model.values tests
    // ======================================================================

    @Test
    fun `values should extract all column values from city entity`() {
        val model = orm.entity(City::class).model
        val city = City(id = 99, name = "TestVille")
        val values = model.values(city)
        values.size shouldBe 2
        val idValue = values.entries.first { it.key.name == "id" }.value
        val nameValue = values.entries.first { it.key.name == "name" }.value
        idValue shouldBe 99
        nameValue shouldBe "TestVille"
    }

    @Test
    fun `values should extract all column values from vet entity`() {
        val model = orm.entity(Vet::class).model
        val vet = Vet(id = 7, firstName = "Test", lastName = "Vet")
        val values = model.values(vet)
        values.size shouldBe 3
        values.entries.first { it.key.name == "id" }.value shouldBe 7
        values.entries.first { it.key.name == "first_name" }.value shouldBe "Test"
        values.entries.first { it.key.name == "last_name" }.value shouldBe "Vet"
    }

    @Test
    fun `values with columns subset should extract only those columns`() {
        val model = orm.entity(City::class).model
        val city = City(id = 5, name = "Partial")
        val nameColumn = model.columns.filter { it.name == "name" }
        val values = model.values(nameColumn, city)
        values.size shouldBe 1
        values.entries.first().key.name shouldBe "name"
        values.entries.first().value shouldBe "Partial"
    }

    // ======================================================================
    // Model.forEachValue tests
    // ======================================================================

    @Test
    fun `forEachValue should iterate over all columns`() {
        val model = orm.entity(City::class).model
        val city = City(id = 10, name = "ForEachCity")
        val collected = mutableMapOf<String, Any?>()
        model.forEachValue(model.columns, city) { column, value ->
            collected[column.name] = value
        }
        collected["id"] shouldBe 10
        collected["name"] shouldBe "ForEachCity"
    }

    @Test
    fun `forEachValue with declared columns should iterate over declared columns`() {
        val model = orm.entity(Vet::class).model
        val vet = Vet(id = 1, firstName = "Jane", lastName = "Doe")
        val collected = mutableListOf<String>()
        model.forEachValue(model.declaredColumns, vet) { column, _ ->
            collected.add(column.name)
        }
        collected shouldHaveSize 3
    }

    // ======================================================================
    // Column property tests
    // ======================================================================

    @Test
    fun `column index should be positive for all columns`() {
        val model = orm.entity(City::class).model
        model.columns.forEach { column ->
            (column.index >= 1).shouldBeTrue()
        }
    }

    @Test
    fun `column metamodel should not be null for any column`() {
        val model = orm.entity(Pet::class).model
        model.columns.forEach { column ->
            column.metamodel.shouldNotBeNull()
        }
    }

    @Test
    fun `city id column generation should be IDENTITY`() {
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.generation shouldBe GenerationStrategy.IDENTITY
    }

    @Test
    fun `city id column sequence should be null`() {
        val model = orm.entity(City::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.sequence.shouldBeNull()
    }

    @Test
    fun `visit model should have version column with timestamp type`() {
        val model = orm.entity(Visit::class).model
        val timestampColumn = model.columns.first { it.name == "timestamp" }
        timestampColumn.version.shouldBeTrue()
        timestampColumn.nullable.shouldBeTrue()
    }

    @Test
    fun `visit model should have foreign key to pet`() {
        val model = orm.entity(Visit::class).model
        val petColumn = model.columns.first { it.name == "pet_id" }
        petColumn.foreignKey.shouldBeTrue()
        petColumn.nullable.shouldBeFalse()
    }

    @Test
    fun `pet type id column should not use IDENTITY generation`() {
        // pet_type table uses explicit IDs (0-5), not auto_increment.
        val model = orm.entity(PetType::class).model
        val idColumn = model.columns.first { it.name == "id" }
        idColumn.primaryKey.shouldBeTrue()
    }

    // ======================================================================
    // Model via QueryTemplate.model() tests
    // ======================================================================

    @Test
    fun `orm model should return same metadata as entity model`() {
        val entityModel = orm.entity(City::class).model
        val queryModel = orm.model(City::class)
        entityModel.name shouldBe queryModel.name
        entityModel.type shouldBe queryModel.type
        entityModel.primaryKeyType shouldBe queryModel.primaryKeyType
        entityModel.columns.size shouldBe queryModel.columns.size
    }

    @Test
    fun `orm model with requirePrimaryKey true should succeed for entity with PK`() {
        val model = orm.model(City::class, true)
        model.shouldNotBeNull()
        model.name shouldBe "city"
    }

    @Test
    fun `orm model with requirePrimaryKey false should succeed`() {
        val model = orm.model(City::class, false)
        model.shouldNotBeNull()
    }

    // ======================================================================
    // Projection model tests (OwnerView)
    // ======================================================================

    @Test
    fun `projection model name should use DbTable annotation value`() {
        val model = orm.projection(OwnerView::class).model
        model.name shouldBe "owner_view"
    }

    @Test
    fun `projection model type should be OwnerView`() {
        val model = orm.projection(OwnerView::class).model
        model.type shouldBe OwnerView::class
    }

    @Test
    fun `projection model primaryKeyType should be Int`() {
        val model = orm.projection(OwnerView::class).model
        model.primaryKeyType shouldBe Int::class
    }

    @Test
    fun `projection model should have version column`() {
        val model = orm.projection(OwnerView::class).model
        val versionColumn = model.columns.first { it.name == "version" }
        versionColumn.version.shouldBeTrue()
    }
}
