package st.orm.serialization

import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.*
import st.orm.serialization.model.Address
import st.orm.serialization.model.Person
import st.orm.template.ORMTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import kotlinx.serialization.json.Json as JsonMapper

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@DataJpaTest(showSql = false)
open class JsonIntegrationTest(
    @Autowired val dataSource: DataSource
) {

    @Serializable
    @DbTable("owner")
    data class SerializableOwner(
        @PK val id: Int = 0,
        override val firstName: String,
        override val lastName: String,
        @Json val address: Address,
        val telephone: String?
    ) : Entity<Int>, Person

    @Test
    fun testOwner() {
        val orm = ORMTemplate.of(dataSource)
        val owner = orm.entity(SerializableOwner::class).getById(1)
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(owner)
        Assertions.assertEquals("""{"id":1,"firstName":"Betty","lastName":"Davis","address":{"address":"638 Cardinal Ave.","city":"Sun Prairie"},"telephone":"6085551749"}""", json)
        val fromJson = jsonMapper.decodeFromString<SerializableOwner>(json)
        Assertions.assertEquals(owner, fromJson)
    }

    @Serializable
    data class SerializablePerson(val firstName: String, val lastName: String)

    @Serializable
    @DbTable("owner")
    data class OwnerWithJsonPerson(
            @PK val id: Int,
            @Json val person: SerializablePerson,
            @Json val address: Address,
            val telephone: String?
    ) : Entity<Int>

    @Test
    fun testOwnerWithJsonPerson()  {
        val orm = ORMTemplate.of(dataSource)
        val query = orm.query("SELECT id, JSON_OBJECT('firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner LIMIT 1")
        val owner = query.getSingleResult(OwnerWithJsonPerson::class)
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(owner);
        Assertions.assertEquals("""{"id":1,"person":{"firstName":"Betty","lastName":"Davis"},"address":{"address":"638 Cardinal Ave.","city":"Sun Prairie"},"telephone":"6085551749"}""", json);
        val fromJson = jsonMapper.decodeFromString<OwnerWithJsonPerson>(json)
        Assertions.assertEquals(owner, fromJson);
    }

    @Serializable
    @DbTable("pet_type")
    data class SerializablePetType(
        @PK val id: Int = 0,
        val name: String
    ) : Entity<Int>

    @Suppress("OPT_IN_USAGE")
    @Serializer(forClass = LocalDate::class)
    object DateSerializer : KSerializer<LocalDate> {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        override fun serialize(encoder: Encoder, value: LocalDate) {
            encoder.encodeString(value.format(formatter))
        }

        override fun deserialize(decoder: Decoder): LocalDate {
            return LocalDate.parse(decoder.decodeString(), formatter)
        }
    }

    @DbTable("pet")
    @Serializable
    data class SerializablePet(
        @PK val id: Int = 0,
        val name: String,
        @Serializable(with = DateSerializer::class) @Persist(updatable = false) val birthDate: LocalDate,
        @FK("type_id") @Persist(updatable = false) val petType: SerializablePetType,
        @FK val owner: SerializableOwner? = null
    ) : Entity<Int>

    @Test
    fun testPet() {
        val orm = ORMTemplate.of(dataSource)
        val pet = orm.entity(SerializablePet::class).getById(1);
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(pet);
        Assertions.assertEquals("""{"id":1,"name":"Leo","birthDate":"2020-09-07","petType":{"id":1,"name":"cat"},"owner":{"id":1,"firstName":"Betty","lastName":"Davis","address":{"address":"638 Cardinal Ave.","city":"Sun Prairie"},"telephone":"6085551749"}}""", json);
        val fromJson = jsonMapper.decodeFromString<SerializablePet>(json);
        Assertions.assertEquals(pet, fromJson);
    }

    @Serializable
    @DbTable("pet")
    data class PetWithRefOwner(
        @PK val id: Int = 0,
        val name: String,
        @Serializable(with = DateSerializer::class) @Persist(updatable = false) val birthDate: LocalDate,
        @FK("type_id") @Persist(updatable = false) val petType: SerializablePetType,
        @Contextual @FK val owner: Ref<SerializableOwner>?
    ) : Entity<Int>

    @Test
    fun testPetWithRef() {
        val orm = ORMTemplate.of(dataSource)
        val pet = orm.entity(PetWithRefOwner::class).getById(1);
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(pet)
        Assertions.assertEquals("""{"id":1,"name":"Leo","birthDate":"2020-09-07","petType":{"id":1,"name":"cat"},"owner":1}""", json);
        val fromJson = jsonMapper.decodeFromString<PetWithRefOwner>(json)
        Assertions.assertEquals(pet, fromJson);
    }

    @Test
    fun testPetWithRefLoaded() {
        val orm = ORMTemplate.of(dataSource)
        val pet = orm.entity(PetWithRefOwner::class).getById(1);
        val owner = pet.owner!!.fetch()
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(pet)
        Assertions.assertEquals("""{"id":1,"name":"Leo","birthDate":"2020-09-07","petType":{"id":1,"name":"cat"},"owner":{"@entity":{"id":1,"firstName":"Betty","lastName":"Davis","address":{"address":"638 Cardinal Ave.","city":"Sun Prairie"},"telephone":"6085551749"}}}""", json);
        val fromJson = jsonMapper.decodeFromString<PetWithRefOwner>(json)
        Assertions.assertEquals(pet, fromJson);
        Assertions.assertEquals(owner, fromJson.owner!!.fetch());
    }

    @Serializable
    @DbTable("owner")
    data class ProjectionOwner(
        @PK val id: Int = 0,
        val firstName: String,
        val lastName: String,
        @Json val address: Address,
        val telephone: String?
    ) : Projection<Int>

    @Serializable
    @DbTable("pet")
    data class PetWithProjectionRefOwner(
        @PK val id: Int = 0,
        val name: String,
        @Serializable(with = DateSerializer::class) @Persist(updatable = false) val birthDate: LocalDate,
        @FK("type_id") @Persist(updatable = false) val petType: SerializablePetType,
        @Contextual @FK val owner: Ref<ProjectionOwner>?
    ) : Entity<Int>

    @Test
    fun testPetWithProjectionRef() {
        val orm = ORMTemplate.of(dataSource)
        val pet = orm.entity(PetWithProjectionRefOwner::class).getById(1)
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(pet)
        Assertions.assertEquals("""{"id":1,"name":"Leo","birthDate":"2020-09-07","petType":{"id":1,"name":"cat"},"owner":1}""", json)
        val fromJson = jsonMapper.decodeFromString<PetWithProjectionRefOwner>(json)
        Assertions.assertEquals(pet, fromJson)
    }

    @Test
    fun testPetWithProjectionRefLoaded() {
        val orm = ORMTemplate.of(dataSource)
        val pet = orm.entity(PetWithProjectionRefOwner::class).getById(1)
        val owner = pet.owner!!.fetch()
        val jsonMapper = JsonMapper {
            serializersModule = StormSerializers
        }
        val json = jsonMapper.encodeToString(pet)
        Assertions.assertEquals("""{"id":1,"name":"Leo","birthDate":"2020-09-07","petType":{"id":1,"name":"cat"},"owner":{"@id":1,"@projection":{"id":1,"firstName":"Betty","lastName":"Davis","address":{"address":"638 Cardinal Ave.","city":"Sun Prairie"},"telephone":"6085551749"}}}""", json)
        val fromJson = jsonMapper.decodeFromString<PetWithProjectionRefOwner>(json)
        Assertions.assertEquals(pet, fromJson)
        Assertions.assertEquals(owner, fromJson.owner!!.fetch())
    }
}