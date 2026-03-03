package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Ref;
import st.orm.core.IntegrationConfig;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.Visit;
import st.orm.core.model.polymorphic.Animal;
import st.orm.core.model.polymorphic.Cat;
import st.orm.core.model.polymorphic.Comment;
import st.orm.core.model.polymorphic.Dog;
import st.orm.core.model.polymorphic.IntDiscAnimal;
import st.orm.core.model.polymorphic.IntDiscCat;
import st.orm.core.model.polymorphic.IntDiscDog;
import st.orm.core.model.polymorphic.JoinedAnimal;
import st.orm.core.model.polymorphic.JoinedCat;
import st.orm.core.model.polymorphic.JoinedDog;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests targeting uncovered branches in RecordMapper:
 * - getSealedFactory: sealed entity hierarchy
 * - sealedCompiledFor: caching behavior
 * - compileSealedPlan: joined entities, extension column validation
 * - calculatePkInfo: entity PK offset calculation
 * - getFieldColumnCount: nested record, polymorphic ref
 * - PolymorphicRefStep: polymorphic FK resolution
 * - RecordStep: nullable nested record, entity cache interactions
 * - RefStep: Ref creation for entity references
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class RecordMapperTest {

    @Autowired
    private DataSource dataSource;

    // Sealed entity factory (single-table inheritance)

    @Test
    public void testSealedEntitySelect() {
        var orm = ORMTemplate.of(dataSource);
        List<Animal> animals = orm.entity(Animal.class).select().getResultList();
        assertEquals(4, animals.size());
        assertInstanceOf(Cat.class, animals.get(0));
        assertInstanceOf(Cat.class, animals.get(1));
        assertInstanceOf(Dog.class, animals.get(2));
        assertInstanceOf(Dog.class, animals.get(3));
    }

    // Sealed entity factory (integer discriminator)

    @Test
    public void testIntegerDiscriminatorSealedEntity() {
        var orm = ORMTemplate.of(dataSource);
        List<IntDiscAnimal> animals = orm.entity(IntDiscAnimal.class).select().getResultList();
        assertEquals(4, animals.size());
        assertInstanceOf(IntDiscCat.class, animals.get(0));
        assertInstanceOf(IntDiscDog.class, animals.get(2));
    }

    // Sealed entity factory (joined-table inheritance)

    @Test
    public void testJoinedTableInheritanceSealedEntity() {
        var orm = ORMTemplate.of(dataSource);
        List<JoinedAnimal> animals = orm.entity(JoinedAnimal.class).select().getResultList();
        assertEquals(3, animals.size());
        assertInstanceOf(JoinedCat.class, animals.get(0));
        assertInstanceOf(JoinedCat.class, animals.get(1));
        assertInstanceOf(JoinedDog.class, animals.get(2));
    }

    // Polymorphic FK resolution (PolymorphicRefStep)

    @Test
    public void testPolymorphicFkResolution() {
        var orm = ORMTemplate.of(dataSource);
        List<Comment> comments = orm.entity(Comment.class).select().getResultList();
        assertEquals(3, comments.size());
        assertNotNull(comments.get(0).target());
        assertNotNull(comments.get(1).target());
    }

    // Nullable nested record mapping (RecordStep null path)

    @Test
    public void testNullableNestedRecord() {
        var orm = ORMTemplate.of(dataSource);
        List<Pet> pets = orm.entity(Pet.class).select().getResultList();
        Pet sly = pets.stream().filter(p -> "Sly".equals(p.name())).findFirst().orElseThrow();
        assertNull(sly.owner());
    }

    // Non-null nested record mapping

    @Test
    public void testNonNullNestedRecord() {
        var orm = ORMTemplate.of(dataSource);
        Pet leo = orm.entity(Pet.class).findById(1).orElseThrow();
        assertNotNull(leo.owner());
        assertEquals("Betty", leo.owner().firstName());
    }

    // Entity interning within query (WeakInterner)

    @Test
    public void testEntityInterningWithinQuery() {
        var orm = ORMTemplate.of(dataSource);
        List<Pet> pets = orm.entity(Pet.class).select().getResultList();
        Pet samantha = pets.stream().filter(p -> "Samantha".equals(p.name())).findFirst().orElseThrow();
        Pet max = pets.stream().filter(p -> "Max".equals(p.name())).findFirst().orElseThrow();
        assertNotNull(samantha.owner());
        assertNotNull(max.owner());
        assertEquals(samantha.owner().id(), max.owner().id());
    }

    // Ref creation for entity references (RefStep)

    @Test
    public void testRefCreation() {
        var orm = ORMTemplate.of(dataSource);
        Pet leo = orm.entity(Pet.class).findById(1).orElseThrow();
        Ref<PetType> typeRef = leo.type();
        assertNotNull(typeRef);
    }

    // Multiple sealed entity queries (exercises sealedCompiledFor cache)

    @Test
    public void testSealedEntityCacheReuse() {
        var orm = ORMTemplate.of(dataSource);
        List<Animal> first = orm.entity(Animal.class).select().getResultList();
        assertEquals(4, first.size());
        List<Animal> second = orm.entity(Animal.class).select().getResultList();
        assertEquals(4, second.size());
    }

    // PetType entity select

    @Test
    public void testPetTypeSelect() {
        var orm = ORMTemplate.of(dataSource);
        PetType catType = orm.entity(PetType.class).findById(0).orElseThrow();
        assertEquals("cat", catType.name());
    }

    // Owner with nested Address/City entity (RecordStep for nested entities)

    @Test
    public void testOwnerWithNestedAddressCity() {
        var orm = ORMTemplate.of(dataSource);
        Owner owner = orm.entity(Owner.class).findById(1).orElseThrow();
        assertNotNull(owner.address());
        assertNotNull(owner.address().city());
        assertEquals("Sun Paririe", owner.address().city().name());
    }

    // Multiple owners sharing the same city (WeakInterner for nested entities)

    @Test
    public void testOwnersShareCityInstance() {
        var orm = ORMTemplate.of(dataSource);
        List<Owner> owners = orm.entity(Owner.class).select().getResultList();
        Owner george = owners.stream().filter(o -> "George".equals(o.firstName())).findFirst().orElseThrow();
        Owner peter = owners.stream().filter(o -> "Peter".equals(o.firstName())).findFirst().orElseThrow();
        assertEquals(george.address().city().id(), peter.address().city().id());
        assertTrue(george.address().city() == peter.address().city(),
                "Owners sharing the same city should reference the same interned City instance");
    }

    // Visit entity with nested Pet

    @Test
    public void testVisitWithNestedPet() {
        var orm = ORMTemplate.of(dataSource);
        Visit visit = orm.entity(Visit.class).findById(1).orElseThrow();
        assertNotNull(visit);
        assertNotNull(visit.pet());
        assertEquals("Samantha", visit.pet().name());
    }

    // getResultStream typed with entity class

    @Test
    public void testGetResultStreamWithEntityClass() {
        var orm = ORMTemplate.of(dataSource);
        try (var stream = orm.selectFrom(City.class).build().getResultStream(City.class)) {
            List<City> cities = stream.toList();
            assertEquals(6, cities.size());
        }
    }
}
