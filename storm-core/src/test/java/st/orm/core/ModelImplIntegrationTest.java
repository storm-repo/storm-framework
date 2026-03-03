package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.Specialty;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.Visit;
import st.orm.core.model.polymorphic.Animal;
import st.orm.core.model.polymorphic.Cat;
import st.orm.core.model.polymorphic.Dog;
import st.orm.core.model.polymorphic.JoinedAnimal;
import st.orm.core.template.Column;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests for {@code ModelImpl} covering model introspection, column metadata,
 * value extraction, sealed entity handling, inline records, and foreign key expansion.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class ModelImplIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // Basic model properties for City

    @Test
    public void testCityModelType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertEquals(City.class, model.type());
    }

    @Test
    public void testCityModelName() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertEquals("city", model.name());
    }

    @Test
    public void testCityModelSchema() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        // City does not specify a schema, should be empty string.
        assertEquals("", model.schema());
    }

    @Test
    public void testCityModelPrimaryKeyType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertEquals(Integer.class, model.primaryKeyType());
    }

    @Test
    public void testCityModelIsDefaultPrimaryKeyNull() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertTrue(model.isDefaultPrimaryKey(null));
    }

    @Test
    public void testCityModelIsDefaultPrimaryKeyZero() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertTrue(model.isDefaultPrimaryKey(0));
    }

    @Test
    public void testCityModelIsNotDefaultPrimaryKeyNonZero() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertFalse(model.isDefaultPrimaryKey(1));
    }

    @Test
    public void testCityModelIsNotJoinedInheritance() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertFalse(model.isJoinedInheritance());
    }

    // Column metadata

    @Test
    public void testCityModelColumnCount() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertEquals(2, model.columns().size());
        assertEquals(2, model.declaredColumns().size());
    }

    @Test
    public void testCityModelColumnProperties() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        List<Column> columns = model.declaredColumns();
        // First column: id (primary key)
        Column idColumn = columns.get(0);
        assertTrue(idColumn.primaryKey());
        assertFalse(idColumn.foreignKey());
        // Second column: name
        Column nameColumn = columns.get(1);
        assertFalse(nameColumn.primaryKey());
        assertFalse(nameColumn.foreignKey());
    }

    @Test
    public void testCityModelPrimaryKeyMetamodel() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        var primaryKeyMetamodel = model.getPrimaryKeyMetamodel();
        assertTrue(primaryKeyMetamodel.isPresent());
    }

    // Value extraction for City

    @Test
    public void testCityModelDeclaredValues() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        City city = City.builder().id(42).name("TestCity").build();
        var values = model.declaredValues(city);
        assertEquals(2, values.size());
        // Values should contain id=42 and name="TestCity"
        var valueList = values.values().stream().toList();
        assertEquals(42, valueList.get(0));
        assertEquals("TestCity", valueList.get(1));
    }

    @Test
    public void testCityModelAllValues() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        City city = City.builder().id(1).name("Sun Paririe").build();
        var values = model.values(city);
        assertEquals(2, values.size());
    }

    // Owner model with inline Address

    @Test
    public void testOwnerModelExpandedColumns() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var columns = model.columns();
        // Owner has: id, firstName, lastName, address, city_id (from Address.city FK),
        //           then expanded city (city.id, city.name), telephone, version
        // Declared columns should be fewer than all columns (no expansion)
        assertTrue(columns.size() > model.declaredColumns().size());
    }

    @Test
    public void testOwnerModelDeclaredColumnsIncludeForeignKey() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var declaredColumns = model.declaredColumns();
        // At least one declared column should be a foreign key.
        assertTrue(declaredColumns.stream().anyMatch(Column::foreignKey),
                "Owner model should have a foreign key column for city_id");
    }

    @Test
    public void testOwnerModelFindMetamodelForCity() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var cityMetamodel = model.findMetamodel(City.class);
        assertTrue(cityMetamodel.isPresent(), "Owner should have a metamodel reference to City");
    }

    @Test
    public void testOwnerModelFindMetamodelForSelf() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var selfMetamodel = model.findMetamodel(Owner.class);
        assertTrue(selfMetamodel.isPresent());
    }

    @Test
    public void testOwnerModelFindMetamodelForUnrelatedType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        // Owner has no FK to Specialty, so findMetamodel should return empty.
        var specialtyMetamodel = model.findMetamodel(Specialty.class);
        assertFalse(specialtyMetamodel.isPresent());
    }

    @Test
    public void testOwnerModelDeclaredValues() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        Owner owner = orm.entity(Owner.class).getById(1);
        var declaredValues = model.declaredValues(owner);
        assertTrue(declaredValues.size() >= 5, "Owner declared values should include all declared columns");
        // Verify the values map contains entries for all declared columns.
        assertEquals(model.declaredColumns().size(), declaredValues.size());
    }

    // Pet model with FK to PetType (Ref) and Owner

    @Test
    public void testPetModelExpandedColumns() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Pet.class).model();
        var columns = model.columns();
        // Pet expands FK references (type -> PetType, owner -> Owner -> Address -> City)
        assertTrue(columns.size() > model.declaredColumns().size());
    }

    @Test
    public void testPetModelForeignKeyColumns() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Pet.class).model();
        var declaredColumns = model.declaredColumns();
        long foreignKeyCount = declaredColumns.stream().filter(Column::foreignKey).count();
        // Pet has FK to PetType (type_id) and Owner (owner_id)
        assertTrue(foreignKeyCount >= 2);
    }

    @Test
    public void testPetModelDeclaredValuesWithNullOwner() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Pet.class).model();
        // Pet id=13 (Sly) has NULL owner
        Pet pet = orm.entity(Pet.class).getById(13);
        var declaredValues = model.declaredValues(pet);
        assertEquals(model.declaredColumns().size(), declaredValues.size());
    }

    // Sealed entity (Single-Table Inheritance): Animal

    @Test
    public void testAnimalModelType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        assertEquals(Animal.class, model.type());
    }

    @Test
    public void testAnimalModelIsNotJoinedInheritance() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        assertFalse(model.isJoinedInheritance());
    }

    @Test
    public void testAnimalModelDeclaredValuesForCat() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Cat cat = new Cat(1, "Whiskers", true);
        var declaredValues = model.declaredValues(cat);
        assertNotNull(declaredValues);
        // Should include discriminator, id, name, indoor, and NULL for weight.
        assertTrue(declaredValues.size() >= 4);
    }

    @Test
    public void testAnimalModelDeclaredValuesForDog() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Dog dog = new Dog(3, "Rex", 30);
        var declaredValues = model.declaredValues(dog);
        assertNotNull(declaredValues);
        // Should include discriminator, id, name, weight, and NULL for indoor.
        assertTrue(declaredValues.size() >= 4);
    }

    @Test
    public void testAnimalModelColumnsIncludeDiscriminator() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        var columns = model.declaredColumns();
        // The animal model should have a discriminator column.
        assertTrue(columns.size() >= 4, "Animal model should include discriminator + fields from all subtypes");
    }

    // Sealed entity (Joined Table Inheritance): JoinedAnimal

    @Test
    public void testJoinedAnimalModelType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(JoinedAnimal.class).model();
        assertEquals(JoinedAnimal.class, model.type());
    }

    @Test
    public void testJoinedAnimalModelIsJoinedInheritance() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(JoinedAnimal.class).model();
        assertTrue(model.isJoinedInheritance());
    }

    // VetSpecialty compound PK

    @Test
    public void testVetSpecialtyModelCompoundPk() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(VetSpecialty.class).model();
        var declaredColumns = model.declaredColumns();
        long primaryKeyColumnCount = declaredColumns.stream().filter(Column::primaryKey).count();
        assertEquals(2, primaryKeyColumnCount, "VetSpecialty should have 2 primary key columns");
    }

    // Visit model with @Version timestamp

    @Test
    public void testVisitModelPrimaryKeyType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Visit.class).model();
        assertEquals(Integer.class, model.primaryKeyType());
    }

    @Test
    public void testVisitModelDeclaredValues() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Visit.class).model();
        Visit visit = orm.entity(Visit.class).getById(1);
        var declaredValues = model.declaredValues(visit);
        assertEquals(model.declaredColumns().size(), declaredValues.size());
        // Verify that LocalDate is mapped to java.sql.Date.
        var valueList = declaredValues.values().stream().toList();
        boolean hasDateValue = valueList.stream().anyMatch(v -> v instanceof Date);
        assertTrue(hasDateValue, "LocalDate should be mapped to java.sql.Date");
    }

    @Test
    public void testVisitModelTimestampVersionMapping() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Visit.class).model();
        Visit visit = orm.entity(Visit.class).getById(1);
        var declaredValues = model.declaredValues(visit);
        // Verify that Instant (timestamp) is mapped to java.sql.Timestamp.
        var valueList = declaredValues.values().stream().toList();
        boolean hasTimestampValue = valueList.stream()
                .anyMatch(v -> v instanceof java.sql.Timestamp);
        assertTrue(hasTimestampValue, "Instant should be mapped to java.sql.Timestamp");
    }

    // RecordType

    @Test
    public void testModelRecordType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        assertNotNull(model.recordType());
        assertEquals(City.class, model.recordType().type());
    }

    @Test
    public void testOwnerModelRecordType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        assertNotNull(model.recordType());
        assertEquals(Owner.class, model.recordType().type());
    }

    // Column index ordering

    @Test
    public void testColumnsAreOrderedByIndex() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var columns = model.columns();
        for (int i = 1; i < columns.size(); i++) {
            assertTrue(columns.get(i).index() > columns.get(i - 1).index(),
                    "Columns must be strictly ordered by index");
        }
    }

    @Test
    public void testDeclaredColumnsAreOrderedByIndex() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        var declaredColumns = model.declaredColumns();
        for (int i = 1; i < declaredColumns.size(); i++) {
            assertTrue(declaredColumns.get(i).index() > declaredColumns.get(i - 1).index(),
                    "Declared columns must be strictly ordered by index");
        }
    }

    // Column names

    @Test
    public void testCityColumnNames() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(City.class).model();
        var declaredColumns = model.declaredColumns();
        assertEquals("id", declaredColumns.get(0).name());
        assertEquals("name", declaredColumns.get(1).name());
    }

    // PetType model (non-auto-generated PK)

    @Test
    public void testPetTypeModelProperties() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(PetType.class).model();
        assertEquals(PetType.class, model.type());
        assertEquals("pet_type", model.name());
        assertEquals(Integer.class, model.primaryKeyType());
        assertFalse(model.isJoinedInheritance());
    }

    @Test
    public void testPetTypeModelDeclaredValues() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(PetType.class).model();
        PetType petType = PetType.builder().id(0).name("cat").build();
        var values = model.declaredValues(petType);
        assertEquals(2, values.size());
        var valueList = values.values().stream().toList();
        assertEquals(0, valueList.get(0));
        assertEquals("cat", valueList.get(1));
    }

    // Model values() with all columns (includes expanded FKs)

    @Test
    public void testOwnerModelAllValuesIncludeExpandedFKs() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Owner.class).model();
        Owner owner = orm.entity(Owner.class).getById(1);
        var allValues = model.values(owner);
        // All values should include expanded FK columns (city name, etc.)
        assertTrue(allValues.size() > model.declaredColumns().size(),
                "values() should include expanded FK columns");
    }

    // Sealed entity discriminator values

    @Test
    public void testAnimalModelDeclaredValuesContainDiscriminator() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Cat cat = new Cat(1, "Whiskers", true);
        var values = model.declaredValues(cat);
        // Verify discriminator is present with correct value for Cat.
        var valueList = values.values().stream().toList();
        assertTrue(valueList.stream().anyMatch(v -> "Cat".equals(v)),
                "Declared values for Cat should include 'Cat' discriminator value");
    }

    @Test
    public void testAnimalModelDogDiscriminatorValue() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Dog dog = new Dog(3, "Rex", 30);
        var values = model.declaredValues(dog);
        var valueList = values.values().stream().toList();
        assertTrue(valueList.stream().anyMatch(v -> "Dog".equals(v)),
                "Declared values for Dog should include 'Dog' discriminator value");
    }

    @Test
    public void testAnimalModelCatNullsWeightColumn() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Cat cat = new Cat(1, "Whiskers", true);
        var values = model.declaredValues(cat);
        // Cat doesn't have a weight field, so the weight column should be null.
        var valueList = values.values().stream().toList();
        // Values: discriminator, id, name, indoor, weight (null for Cat).
        long nullCount = valueList.stream().filter(v -> v == null).count();
        assertTrue(nullCount >= 1,
                "Cat should have at least one null value (weight column)");
    }

    @Test
    public void testAnimalModelDogNullsIndoorColumn() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Animal.class).model();
        Dog dog = new Dog(3, "Rex", 30);
        var values = model.declaredValues(dog);
        // Dog doesn't have an indoor field, so the indoor column should be null.
        var valueList = values.values().stream().toList();
        long nullCount = valueList.stream().filter(v -> v == null).count();
        assertTrue(nullCount >= 1,
                "Dog should have at least one null value (indoor column)");
    }

    // Joined inheritance model introspection

    @Test
    public void testJoinedAnimalModelDeclaredColumns() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(JoinedAnimal.class).model();
        var declaredColumns = model.declaredColumns();
        // Joined inheritance model should have columns from all subtypes.
        assertTrue(declaredColumns.size() >= 3,
                "JoinedAnimal model should have columns from all joined subtypes");
    }

    @Test
    public void testJoinedAnimalModelPrimaryKeyType() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(JoinedAnimal.class).model();
        assertEquals(Integer.class, model.primaryKeyType());
    }

    // VetSpecialty model with compound non-auto-gen PK

    @Test
    public void testVetSpecialtyModelIsNotAutoGen() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(VetSpecialty.class).model();
        // VetSpecialty has @PK(generation = NONE), so default PK should be the zero-valued compound key.
        VetSpecialtyPK zeroPk = VetSpecialtyPK.builder().vetId(0).specialtyId(0).build();
        assertTrue(model.isDefaultPrimaryKey(zeroPk));
    }

    @Test
    public void testVetSpecialtyModelNonDefaultPk() {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(VetSpecialty.class).model();
        VetSpecialtyPK nonZeroPk = VetSpecialtyPK.builder().vetId(2).specialtyId(1).build();
        assertFalse(model.isDefaultPrimaryKey(nonZeroPk));
    }

    // Pet model: all values() includes expanded FK entity graph

    @Test
    public void testPetModelAllValuesExpandForeignKeys() throws Exception {
        var orm = ORMTemplate.of(dataSource);
        var model = orm.entity(Pet.class).model();
        Pet pet = orm.entity(Pet.class).getById(1);
        var allValues = model.values(pet);
        // Pet expands type (PetType) and owner (Owner -> Address -> City).
        assertTrue(allValues.size() > model.declaredColumns().size(),
                "Pet values() should expand foreign key columns");
    }
}
