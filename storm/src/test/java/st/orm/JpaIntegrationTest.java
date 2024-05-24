package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetTypeEnum;
import st.orm.model.Vet;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.template.JpaTemplate.ORM;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class JpaIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testSelectVet() {
        try (var query = ORM(entityManager)."SELECT * FROM vet".prepare()) {
            var stream = query.getResultStream();
            assertEquals(6, stream.map(Arrays::asList).count());
        }
    }

    @Test
    public void testSelectPet() {
        try (var query = ORM(entityManager)."""
            SELECT p.id, p.name, p.birth_date, pt.*, o.* 
            FROM pet p 
              INNER JOIN pet_type pt ON p.type_id = pt.id 
              INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
            var stream = query.getResultStream();
            assertEquals(12, stream.map(Arrays::asList).count());
        }
    }

    @Test
    public void testSelectPetTyped() {
        assertThrows(PersistenceException.class, () -> {
            try (var query = ORM(entityManager)."""
                     SELECT p.id, p.name, p.birth_date, pt.*, o.* 
                     FROM pet p 
                       INNER JOIN pet_type pt ON p.type_id = pt.id 
                       INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                assertEquals(10, stream
                        .map(Pet::owner)
                        .filter(Objects::nonNull)
                        .map(Owner::firstName)
                        .distinct()
                        .count());
            }
        });
    }

    @Test
    public void testSelectPetTypedWithFilter() {
        assertThrows(PersistenceException.class, () -> {
            String nameFilter = "%y%";
            try (var query = ORM(entityManager)."""
                SELECT p.id, p.name, p.birth_date, pt.*, o.*
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
                  INNER JOIN owner o ON p.owner_id = o.id
                WHERE p.name LIKE \{nameFilter}""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                assertEquals(5, stream
                        .map(Pet::owner)
                        .filter(Objects::nonNull)
                        .map(Owner::firstName)
                        .distinct()
                        .count());
            }
        });
    }

    @Test
    public void testSelectPetTypedWithEnum() {
        try (var query = ORM(entityManager)."""
            SELECT UPPER(pt.name) pet_type
            FROM pet p
              INNER JOIN pet_type pt ON p.type_id = pt.id
              INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
            var stream = query.getResultStream(PetTypeEnum.class);
            assertEquals(6, stream.distinct().count());
        }
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnum() {
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type, Owner owner) {
            }
            try (var query = ORM(entityManager)."""
                    SELECT p.id, p.name, p.birth_date, UPPER(pt.name) pet_type, o.*
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id
                      INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                assertEquals(6, stream.map(Pet::type).distinct().count());
            }
        });
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnumNull() {
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type, Owner owner) {}
            try (var query = ORM(entityManager)."""
                    SELECT p.id, p.name, p.birth_date, NULL pet_type, o.*
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id
                      INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndNonnullEnumNull() {
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, @Nonnull PetTypeEnum type, Owner owner) {}
            try (var query = ORM(entityManager)."""
                    SELECT p.id, p.name, p.birth_date, NULL pet_type, o.*
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id
                      INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetTypedWithLocalRecordAndEnumNotExists() {
        assertThrows(PersistenceException.class, () -> {
            record Pet(int id, String name, LocalDate birthDate, PetTypeEnum type, Owner owner) {}
            try (var query = ORM(entityManager)."""
                    SELECT p.id, p.name, p.birth_date, 'fail' pet_type, o.*
                    FROM pet p
                      INNER JOIN pet_type pt ON p.type_id = pt.id
                      INNER JOIN owner o ON p.owner_id = o.id""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetWithoutType() {
        assertThrows(PersistenceException.class, ()-> {
            try (var query = ORM(entityManager)."""
                SELECT p.id, p.name, p.birth_date, pt.*, o.*
                FROM pet p
                  INNER JOIN owner o ON p.owner_id = o.id
                  LEFT OUTER JOIN pet_type pt ON p.type_id = pt.id AND 1 <> 1""".prepare();
                 var stream = query.getResultStream(Pet.class)) {
                stream.toList();
            }
        });
    }

    @Test
    public void testSelectPetWithoutOwner() {
        assertThrows(PersistenceException.class, ()-> {
            try (var query = ORM(entityManager)."""
                SELECT p.id, p.name, p.birth_date, pt.*, o.*
                FROM pet p
                  INNER JOIN pet_type pt ON p.type_id = pt.id
                  LEFT OUTER JOIN owner o ON p.owner_id = o.id AND 1 <> 1""".prepare()) {
                var stream = query.getResultStream(Pet.class);
                stream.toList();
            }
        });
    }


    @Test
    public void testSelectVetRecord() {
        try (var query = ORM(entityManager)."SELECT \{Vet.class} FROM \{Vet.class}".prepare()) {
            var stream = query.getResultStream();
            stream.toList();
        }
    }
}
