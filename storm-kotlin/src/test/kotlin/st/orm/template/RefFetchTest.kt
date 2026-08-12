package st.orm.template

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.junit.jupiter.SpringExtension
import st.orm.Metamodel
import st.orm.PersistenceException
import st.orm.template.model.Adoption
import st.orm.template.model.Animal
import st.orm.template.model.Owner
import st.orm.template.model.Pet
import st.orm.template.model.PetOwnerRef
import st.orm.template.model.PetType

/**
 * Verifies the Kotlin surface of resolving a reference as part of the query. [PetOwnerRef] maps the pet table with
 * the owner as a reference; [Pet] maps the same table with the owner as an entity, giving an entity-graph baseline.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [IntegrationConfig::class])
@Sql("/data.sql")
internal open class RefFetchTest(
    @Autowired val orm: ORMTemplate,
) {

    private val ownerPath: Metamodel<PetOwnerRef, Owner> = Metamodel.of(PetOwnerRef::class.java, "owner")
    private val namePath: Metamodel<PetOwnerRef, String> = Metamodel.of(PetOwnerRef::class.java, "name")

    @Test
    fun `fetch loads the reference in the same query`() {
        val pets = orm.entity(PetOwnerRef::class).select()
            .fetch(ownerPath)
            .resultList
        pets.shouldNotBeEmpty()
        val withOwner = pets.first { it.owner != null }
        withOwner.owner.shouldNotBeNull().isLoaded shouldBe true
        withOwner.owner.shouldNotBeNull().fetch().shouldNotBeNull()
    }

    @Test
    fun `resolved reference matches the entity graph`() {
        val viaEntity = orm.entity(Pet::class).select()
            .where(Metamodel.of<Pet, String>(Pet::class.java, "name") eq "Leo")
            .resultList
            .map { it.owner }
        val viaRef = orm.entity(PetOwnerRef::class).select()
            .fetch(ownerPath)
            .where(namePath eq "Leo")
            .resultList
            .map { it.owner?.fetch() }
        viaEntity.shouldNotBeEmpty()
        viaRef shouldBe viaEntity
    }

    @Test
    fun `without fetch the reference stays unloaded`() {
        val pets = orm.entity(PetOwnerRef::class).select().resultList
        val withOwner = pets.first { it.owner != null }
        withOwner.owner.shouldNotBeNull().isLoaded shouldBe false
    }

    @Test
    fun `nullable reference yields null`() {
        val pets = orm.entity(PetOwnerRef::class).select()
            .fetch(ownerPath)
            .where(namePath eq "Sly")
            .resultList
        pets.single().owner.shouldBeNull()
    }

    @Test
    fun `fetch is available in the select DSL`() {
        val pets = orm.entity(PetOwnerRef::class).select {
            fetch(ownerPath)
            where(namePath eq "Leo")
        }.resultList
        pets.single().owner.shouldNotBeNull().isLoaded shouldBe true
    }

    @Test
    fun `fetch rejects a path that crosses no reference`() {
        val typePath: Metamodel<PetOwnerRef, PetType> = Metamodel.of(PetOwnerRef::class.java, "type")
        val exception = assertThrows<PersistenceException> {
            orm.entity(PetOwnerRef::class).select().fetch(typePath)
        }
        exception.message.shouldNotBeNull().contains("crosses no reference") shouldBe true
    }

    @Test
    fun `fetch rejects a reference to a sealed type`() {
        val animalPath: Metamodel<Adoption, Animal> = Metamodel.of(Adoption::class.java, "animal")
        assertThrows<PersistenceException> {
            orm.entity(Adoption::class).select().fetch(animalPath)
        }
    }
}
