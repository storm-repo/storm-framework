package st.orm.core;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.core.model.PetTypeEnum;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.core.template.ORMTemplate.of;
import static st.orm.core.template.TemplateString.raw;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class PlainPreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelectVet() {
        try (var query = ORMTemplate.of(dataSource).query("SELECT * FROM vet").prepare();
             var stream = query.getResultStream()) {
            assertEquals(6, stream.count());
        }
    }

    @Test
    public void testSelectPet() {
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
        try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
                  INNER JOIN owner o ON p.owner_id = o.id
                  INNER JOIN city c ON o.city_id = c.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            //noinspection DataFlowIssue
            assertEquals(10, stream.map(x -> x.owner().firstName()).distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithFilter() {
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
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
        try (var query = ORMTemplate.of(dataSource).query("""
                SELECT p.id, p.name, p.birth_date, pt.id, pt.name, o.id, o.first_name, o.last_name, o.address, c.id, c.name, o.telephone, o.version
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
                  LEFT OUTER JOIN owner o ON p.owner_id = o.id AND 1 <> 1
                  LEFT OUTER JOIN city c ON o.city_id = c.id""").prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(13, stream.count());
        }
    }
}