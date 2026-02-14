package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.core.template.TemplateString.raw;

import jakarta.annotation.Nonnull;
import java.time.LocalDate;
import java.util.Objects;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.PetTypeEnum;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class PlainPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelectVet() {
        // data.sql inserts 6 vets (ids 1-6).
        try (var query = ORMTemplate.of(dataSource).query("SELECT * FROM vet").prepare();
             var stream = query.getResultStream()) {
            assertEquals(6, stream.count());
        }
    }

    @Test
    public void testSelectPet() {
        // INNER JOIN on owner filters out pet 13 (Sly, no owner). 12 pets have owners.
        try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
                  INNER JOIN owner o ON p.owner_id = o.id
                  INNER JOIN city c ON o.city_id = c.id""").prepare();
             var stream = query.getResultStream()) {
            assertEquals(12, stream.count());
        }
    }

    @Test
    public void testSelectPetTyped() {
        // INNER JOIN on owner returns 12 pets (excluding Sly). 10 distinct owner first names
        // exist among those 12 pets (each of the 10 owners has a unique first name).
        try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, p.type_id, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN owner o ON p.owner_id = o.id
                  INNER JOIN city c ON o.city_id = c.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            //noinspection DataFlowIssue
            assertEquals(10, stream.map(x -> x.owner().firstName()).distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithFilter() {
        // Filter '%y%' matches: Rosy, Iggy, Lucky(9), Lucky(12), Freddy (5 pets with owners).
        // Sly also matches but has no owner and is excluded by inner join.
        // 5 distinct owner first names: Eduardo, Harold, Jeff, Carlos, David.
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT p.id, p.name, p.birth_date, p.type_id, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN owner o ON p.owner_id = o.id
                  INNER JOIN city c ON o.city_id = c.id
                WHERE p.name LIKE \0""", nameFilter)).prepare();
             var stream = query.getResultStream(Pet.class)) {
            //noinspection DataFlowIssue
            assertEquals(5, stream.map(x -> x.owner().firstName()).distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithEnum() {
        // 12 pets with owners across 6 pet types (cat, dog, lizard, snake, bird, hamster).
        // All 6 types are represented among the 12 owned pets.
        try (var query = ORMTemplate.of(dataSource).query("""
            SELECT UPPER(pt.name) pet_type
            FROM pet p
              INNER JOIN pet_type pt ON p.type_id = pt.id
              INNER JOIN owner o ON p.owner_id = o.id""").prepare();
             var stream = query.getResultStream(PetTypeEnum.class)) {
            assertEquals(6, stream.distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnum() {
        // All 13 pets have a type. 6 distinct pet types exist in data.sql.
        record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type) {}
        try (var query = ORMTemplate.of(dataSource).query("""
            SELECT p.id, p.name, p.birth_date, UPPER(pt.name) pet_type
            FROM pet p
              INNER JOIN pet_type pt ON p.type_id = pt.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(6, stream.map(Pet::type).distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnumNull() {
        // Selecting NULL as pet_type for all 13 pets. All types should be null.
        record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type) {}
        try (var query = ORMTemplate.of(dataSource).query("""
            SELECT p.id, p.name, p.birth_date, NULL pet_type
            FROM pet p
              INNER JOIN pet_type pt ON p.type_id = pt.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(13, stream.map(Pet::type).filter(Objects::isNull).count());
        }
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnumNullNonnull() {
        // Selecting NULL for a @Nonnull enum field should throw PersistenceException (via SqlTemplateException),
        // because the framework detects the null violation before returning results.
        record Pet(int id, String name, LocalDate birthDate, @Nonnull PetTypeEnum type) {}
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, NULL pet_type
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id""");
            query.getResultList(Pet.class);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndNonnullEnumNull() {
        // Same as above but using stream access: NULL for @Nonnull enum should throw during streaming.
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, @Nonnull PetTypeEnum type) {}
            try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, NULL pet_type
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id""").prepare();
                 var stream = query.getResultStream(Pet.class)) {
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnumNotExists() {
        // 'fail' is not a valid PetTypeEnum value; should throw PersistenceException during mapping.
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type) {}
            try (var query = ORMTemplate.of(dataSource).query("""
                    SELECT p.id, p.name, p.birth_date, 'fail' pet_type
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id""").prepare();
                 var stream = query.getResultStream(Pet.class)) {
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetWithoutType() {
        // LEFT OUTER JOIN with "1 <> 1" ensures pet_type columns are always NULL.
        // Pet model requires a non-null @FK PetType, so mapping should throw PersistenceException.
        assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query("""
                    SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                    FROM pet p
                      INNER JOIN owner o ON p.owner_id = o.id
                      INNER JOIN city c ON o.city_id = c.id
                      LEFT OUTER JOIN pet_type pt ON p.type_id = pt.id AND 1 <> 1""").prepare();
                 var stream = query.getResultStream(Pet.class)) {
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetWithoutOwner() {
        // LEFT OUTER JOIN with "1 <> 1" ensures owner columns are always NULL.
        // Local Pet record defines owner as @Nonnull @FK, so mapping should throw PersistenceException.
        record Pet(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Persist(updatable = false) PetType petType,
            @Nonnull @FK Owner owner
        ) {}
        assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query("""
                    SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id
                      LEFT OUTER JOIN owner o ON p.owner_id = o.id AND 1 <> 1""").prepare();
                 var stream = query.getResultStream(Pet.class)) {
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetWithoutOwnerWithNullableOwner() {
        // LEFT OUTER JOIN with "1 <> 1" ensures owner columns are always NULL.
        // Default Pet model has nullable owner, so all 13 pets (from data.sql) should be returned with null owners.
        try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, p.type_id, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  LEFT OUTER JOIN owner o ON p.owner_id = o.id AND 1 <> 1
                  LEFT OUTER JOIN city c ON o.city_id = c.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(13, stream.count());
        }
    }
}
