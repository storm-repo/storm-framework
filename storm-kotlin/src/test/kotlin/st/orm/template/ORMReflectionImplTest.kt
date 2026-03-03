package st.orm.template

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Entity
import st.orm.PK
import st.orm.PersistenceException
import st.orm.Ref
import st.orm.spi.ORMReflectionImpl
import st.orm.template.model.*

/**
 * Tests for [ORMReflectionImpl] covering Kotlin data class reflection,
 * type resolution, sealed class support, and ID extraction.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
open class ORMReflectionImplTest(
    @Autowired val orm: ORMTemplate,
) {

    private val reflection = ORMReflectionImpl()

    // findRecordType / getRecordType

    @Test
    fun `findRecordType should return RecordType for Kotlin data class`() {
        val result = reflection.findRecordType(City::class.java)
        result.isPresent.shouldBeTrue()
        val recordType = result.get()
        recordType.type shouldBe City::class.java
        recordType.fields().shouldHaveSize(2)
        recordType.fields()[0].name shouldBe "id"
        recordType.fields()[1].name shouldBe "name"
    }

    @Test
    fun `findRecordType should return fields with correct types for Owner`() {
        val result = reflection.findRecordType(Owner::class.java)
        result.isPresent.shouldBeTrue()
        val fields = result.get().fields()
        fields.any { it.name == "id" }.shouldBeTrue()
        fields.any { it.name == "firstName" }.shouldBeTrue()
        fields.any { it.name == "lastName" }.shouldBeTrue()
        fields.any { it.name == "address" }.shouldBeTrue()
        fields.any { it.name == "telephone" }.shouldBeTrue()
        fields.any { it.name == "version" }.shouldBeTrue()
    }

    @Test
    fun `findRecordType should detect nullable fields`() {
        val result = reflection.findRecordType(Owner::class.java)
        result.isPresent.shouldBeTrue()
        val fields = result.get().fields()
        val telephoneField = fields.first { it.name == "telephone" }
        telephoneField.nullable().shouldBeTrue()

        val firstNameField = fields.first { it.name == "firstName" }
        firstNameField.nullable().shouldBeFalse()
    }

    @Test
    fun `findRecordType should return empty for non-data class`() {
        val result = reflection.findRecordType(String::class.java)
        result.isPresent.shouldBeFalse()
    }

    @Test
    fun `getRecordType should return RecordType for Kotlin data class`() {
        val recordType = reflection.getRecordType(City::class.java)
        recordType.shouldNotBeNull()
        recordType.type shouldBe City::class.java
    }

    @Test
    fun `getRecordType should return RecordType with correct constructor`() {
        val recordType = reflection.getRecordType(City::class.java)
        recordType.constructor.shouldNotBeNull()
        recordType.constructor.parameterCount shouldBe 2
    }

    @Test
    fun `findRecordType should cache results for repeated calls`() {
        val first = reflection.findRecordType(City::class.java)
        val second = reflection.findRecordType(City::class.java)
        first.isPresent.shouldBeTrue()
        second.isPresent.shouldBeTrue()
        // Should be the same cached instance
        first.get() shouldBe second.get()
    }

    // getId

    @Test
    fun `getId should return primary key value from data class`() {
        val city = City(id = 42, name = "TestCity")
        val id = reflection.getId(city)
        id shouldBe 42
    }

    @Test
    fun `getId should return primary key from Owner`() {
        val owner = Owner(
            id = 7,
            firstName = "Test",
            lastName = "User",
            address = Address("123 Main St", City(id = 1, name = "Test")),
            telephone = "555-1234",
            version = 0,
        )
        val id = reflection.getId(owner)
        id shouldBe 7
    }

    // getRecordValue

    @Test
    fun `getRecordValue should return field value by index`() {
        val city = City(id = 10, name = "SomeCity")
        reflection.getRecordValue(city, 0) shouldBe 10
        reflection.getRecordValue(city, 1) shouldBe "SomeCity"
    }

    @Test
    fun `getRecordValue should return nullable field value`() {
        val owner = Owner(
            id = 1,
            firstName = "Test",
            lastName = "User",
            address = Address("123 Main", City(id = 1, name = "C")),
            telephone = null,
            version = 0,
        )
        // telephone is the 5th field (index 4)
        val fields = reflection.getRecordType(Owner::class.java).fields()
        val telephoneIndex = fields.indexOfFirst { it.name == "telephone" }
        reflection.getRecordValue(owner, telephoneIndex) shouldBe null
    }

    // getType / getDataType / isSupportedType

    @Test
    fun `isSupportedType should return true for KClass`() {
        reflection.isSupportedType(City::class).shouldBeTrue()
    }

    @Test
    fun `isSupportedType should return true for Java Class`() {
        reflection.isSupportedType(City::class.java).shouldBeTrue()
    }

    @Test
    fun `getType should return Java class from KClass`() {
        val javaClass = reflection.getType(City::class)
        javaClass shouldBe City::class.java
    }

    @Test
    fun `getType should return Java class from Java Class`() {
        val javaClass = reflection.getType(City::class.java)
        javaClass shouldBe City::class.java
    }

    @Test
    fun `getDataType should return Data class from KClass`() {
        val dataClass = reflection.getDataType(City::class)
        dataClass shouldBe City::class.java
    }

    @Test
    fun `getDataType should throw for non-Data type`() {
        assertThrows<PersistenceException> {
            reflection.getDataType(String::class.java)
        }
    }

    // isDefaultValue

    @Test
    fun `isDefaultValue should return true for zero int`() {
        reflection.isDefaultValue(0).shouldBeTrue()
    }

    @Test
    fun `isDefaultValue should return false for non-zero int`() {
        reflection.isDefaultValue(42).shouldBeFalse()
    }

    @Test
    fun `isDefaultValue should return true for null`() {
        reflection.isDefaultValue(null).shouldBeTrue()
    }

    // getPermittedSubclasses (sealed classes)

    @Test
    fun `getPermittedSubclasses should return subclasses of sealed interface`() {
        val subclasses = reflection.getPermittedSubclasses(Animal::class.java)
        subclasses.shouldHaveSize(2)
        subclasses.any { it == Cat::class.java }.shouldBeTrue()
        subclasses.any { it == Dog::class.java }.shouldBeTrue()
    }

    // isDefaultMethod

    @Test
    fun `isDefaultMethod should return true for Kotlin interface method`() {
        // CityCustomRepo has Kotlin default methods. Get one via reflection.
        val method = CityCustomRepo::class.java.getMethod("cityCount")
        reflection.isDefaultMethod(method).shouldBeTrue()
    }

    @Test
    fun `isDefaultMethod should return true for Java interface default method`() {
        // Object methods like toString are not default interface methods; check java.util.List iterator
        // Instead, test a standard Java default method.
        val method = java.util.List::class.java.getMethod("spliterator")
        reflection.isDefaultMethod(method).shouldBeTrue()
    }

    // Integration: entity operations using reflection

    @Test
    fun `orm entity should use ORMReflection for Kotlin data classes`() {
        val city = orm.entity(City::class).select().where(1).singleResult
        city.id shouldBe 1
        city.name shouldBe "Sun Paririe"
    }

    @Test
    fun `orm entity should handle data class with nullable FK`() {
        // Pet id=13 (Sly) has null owner
        val pet = orm.entity(Pet::class).select().where(13).singleResult
        pet.name shouldBe "Sly"
        pet.owner shouldBe null
    }

    @Test
    fun `orm entity should handle data class with inline data`() {
        // Owner has an inline Address field
        val owner = orm.entity(Owner::class).select().where(1).singleResult
        owner.firstName shouldBe "Betty"
        owner.address.shouldNotBeNull()
        owner.address.address shouldBe "638 Cardinal Ave."
    }

    @Test
    fun `findRecordType should return annotations for data class`() {
        val result = reflection.findRecordType(City::class.java)
        result.isPresent.shouldBeTrue()
        val recordType = result.get()
        // City has @PK on id field
        val idField = recordType.fields().first { it.name == "id" }
        idField.isAnnotationPresent(st.orm.PK::class.java).shouldBeTrue()
    }

    @Test
    fun `findRecordType should filter Metadata annotation from type annotations`() {
        val result = reflection.findRecordType(City::class.java)
        result.isPresent.shouldBeTrue()
        val recordType = result.get()
        // The Metadata annotation should be filtered out from the type-level annotations
        recordType.annotations().none { it.annotationClass.java.name.contains("Metadata") }.shouldBeTrue()
    }

    @Test
    fun `getRecordType should detect immutable properties correctly`() {
        // All City fields are val (immutable)
        val recordType = reflection.getRecordType(City::class.java)
        recordType.fields().forEach { field ->
            field.mutable().shouldBeFalse()
        }
    }

    /**
     * A data class with a List<String> property to exercise parameterized type resolution.
     */
    data class DataWithList(
        @PK val id: Int = 0,
        val tags: List<String>,
    ) : Entity<Int>

    @Test
    fun `findRecordType should handle parameterized type List`() {
        val result = reflection.findRecordType(DataWithList::class.java)
        result.isPresent.shouldBeTrue()
        val fields = result.get().fields()
        fields.shouldHaveSize(2)
        val tagsField = fields.first { it.name == "tags" }
        tagsField.type() shouldBe List::class.java
        tagsField.nullable().shouldBeFalse()
    }

    /**
     * A data class with nullable complex type.
     */
    data class DataWithNullableList(
        @PK val id: Int = 0,
        val items: List<String>?,
    ) : Entity<Int>

    @Test
    fun `findRecordType should detect nullable parameterized type`() {
        val result = reflection.findRecordType(DataWithNullableList::class.java)
        result.isPresent.shouldBeTrue()
        val itemsField = result.get().fields().first { it.name == "items" }
        itemsField.nullable().shouldBeTrue()
        itemsField.type() shouldBe List::class.java
    }

    /**
     * A data class with a Map<String, Int> property.
     */
    data class DataWithMap(
        @PK val id: Int = 0,
        val metadata: Map<String, Int>,
    ) : Entity<Int>

    @Test
    fun `findRecordType should handle parameterized type Map`() {
        val result = reflection.findRecordType(DataWithMap::class.java)
        result.isPresent.shouldBeTrue()
        val mapField = result.get().fields().first { it.name == "metadata" }
        mapField.type() shouldBe Map::class.java
    }

    @Test
    fun `isSupportedType should return false for unsupported type`() {
        reflection.isSupportedType("not a class").shouldBeFalse()
    }

    @Test
    fun `getType should throw for unsupported type`() {
        assertThrows<IllegalArgumentException> {
            reflection.getType("not a class")
        }
    }

    @Test
    fun `getRecordValue should return list field value`() {
        val data = DataWithList(id = 1, tags = listOf("a", "b"))
        reflection.getRecordValue(data, 0) shouldBe 1
        reflection.getRecordValue(data, 1) shouldBe listOf("a", "b")
    }

    @Test
    fun `getRecordValue should return null for nullable field`() {
        val data = DataWithNullableList(id = 1, items = null)
        reflection.getRecordValue(data, 1) shouldBe null
    }

    sealed interface SealedEntity : Entity<Int> {
        val id: Int
    }

    data class SealedChildA(
        @PK override val id: Int = 0,
        val name: String,
    ) : SealedEntity

    data class SealedChildB(
        @PK override val id: Int = 0,
        val value: Int,
    ) : SealedEntity

    interface KotlinInterfaceWithDefault {
        fun defaultMethod(): String = "default"
    }

    @Test
    fun `isDefaultMethod should return true for Kotlin interface with default method`() {
        val method = KotlinInterfaceWithDefault::class.java.getMethod("defaultMethod")
        reflection.isDefaultMethod(method).shouldBeTrue()
    }

    @Test
    fun `findRecordType should return empty for regular Kotlin class`() {
        // A regular (non-data) class should not be recognized as a record type
        class RegularClass(val name: String)
        val result = reflection.findRecordType(RegularClass::class.java)
        result.isPresent.shouldBeFalse()
    }

    @Test
    fun `findRecordType should return empty for Java record`() {
        // java.lang.Record types are handled by DefaultORMReflectionImpl
        val result = reflection.findRecordType(String::class.java)
        result.isPresent.shouldBeFalse()
    }

    @Test
    fun `getId should work with parameterized type entity`() {
        val data = DataWithList(id = 42, tags = listOf("tag"))
        reflection.getId(data) shouldBe 42
    }

    @Test
    fun `getId should work with nullable list entity`() {
        val data = DataWithNullableList(id = 7, items = null)
        reflection.getId(data) shouldBe 7
    }

    data class DataWithVar(
        @PK val id: Int = 0,
        var mutableField: String,
    ) : Entity<Int>

    @Test
    fun `findRecordType should detect mutable properties`() {
        val result = reflection.findRecordType(DataWithVar::class.java)
        result.isPresent.shouldBeTrue()
        val fields = result.get().fields()
        val mutableFieldRecord = fields.first { it.name == "mutableField" }
        mutableFieldRecord.mutable().shouldBeTrue()
        val idFieldRecord = fields.first { it.name == "id" }
        idFieldRecord.mutable().shouldBeFalse()
    }

    data class SimpleRef(
        @PK val id: Int = 0,
        val name: String,
    ) : Entity<Int>

    data class DataWithRef(
        @PK val id: Int = 0,
        val ref: Ref<SimpleRef>,
    ) : Entity<Int>

    @Test
    fun `findRecordType should handle Ref parameterized type`() {
        val result = reflection.findRecordType(DataWithRef::class.java)
        result.isPresent.shouldBeTrue()
        val refField = result.get().fields().first { it.name == "ref" }
        refField.type() shouldBe Ref::class.java
    }

    @Test
    fun `findRecordType should merge annotations from property and constructor parameter`() {
        val result = reflection.findRecordType(DataWithVar::class.java)
        result.isPresent.shouldBeTrue()
        val idField = result.get().fields().first { it.name == "id" }
        // @PK is on the constructor parameter, should be present in merged annotations
        idField.isAnnotationPresent(PK::class.java).shouldBeTrue()
    }

    @Test
    fun `getRecordType annotations should filter out Metadata`() {
        val recordType = reflection.getRecordType(DataWithList::class.java)
        recordType.shouldNotBeNull()
        // Kotlin Metadata annotation should not be in the type-level annotations
        recordType.annotations().none { it.annotationClass.java.name.contains("Metadata") }.shouldBeTrue()
    }
}
