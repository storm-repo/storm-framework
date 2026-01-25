package st.orm.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Data;
import st.orm.core.model.Address;
import st.orm.core.model.City;
import st.orm.core.model.Owner;
import st.orm.core.model.Pet;
import st.orm.core.model.PetType;
import st.orm.DbColumn;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.TemporalType;
import st.orm.core.model.Pet_;
import st.orm.core.model.Specialty;
import st.orm.core.model.Vet;
import st.orm.core.model.VetSpecialty;
import st.orm.core.model.VetSpecialtyPK;
import st.orm.core.model.Visit;
import st.orm.core.model.Visit_;
import st.orm.core.repository.spring.PetRepository;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlTemplateException;
import st.orm.core.template.TemplateBuilder;
import st.orm.core.template.TemplateString;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.GenerationStrategy.NONE;
import static st.orm.core.template.Templates.alias;
import static st.orm.core.template.Templates.from;
import static st.orm.core.template.Templates.insert;
import static st.orm.core.template.Templates.param;
import static st.orm.core.template.Templates.select;
import static st.orm.core.template.Templates.set;
import static st.orm.core.template.Templates.table;
import static st.orm.core.template.Templates.update;
import static st.orm.core.template.Templates.values;
import static st.orm.core.template.Templates.where;
import static st.orm.core.template.SqlInterceptor.observe;
import static st.orm.core.template.TemplateString.raw;
import static st.orm.Operator.BETWEEN;
import static st.orm.Operator.EQUALS;
import static st.orm.ResolveScope.INNER;
import static st.orm.ResolveScope.OUTER;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class TemplatePreparedStatementIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PetRepository petRepository;

    @Test
    public void testSelectPet() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0""", Pet.class, Pet.class)).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(10, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithFromAndJoins() {
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE p.name LIKE \0""", select(Pet.class), from(Pet.class, "p", true), param(nameFilter))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(5, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithJoinsMetamodel() {
        var visit = ORMTemplate.of(dataSource).entity(Visit.class).getById(1);
        Pet pet = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON \0 = \0
                WHERE \0 = \0""", Pet.class, Pet.class, Visit.class, Pet_.id, Visit_.pet, Visit_.id, visit.id()))
        .getSingleResult(Pet.class);
        assertEquals(pet.id(), visit.pet().id());
    }

    @Test
    public void testSelectPetWithTableAndJoins() {
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE p.name LIKE \0""", select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), param(nameFilter))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(5, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithRecord() {
        record Owner(String firstName, String lastName, String telephone) implements Data {}
        record Pet(String name, LocalDate birthDate, Owner owner) implements Data {}
        String nameFilter = "%y%";
        var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  LEFT OUTER JOIN \0 ON \0.owner_id = \0.id
                WHERE \0.name LIKE \0""", Pet.class, Pet.class, Owner.class, Pet.class, Owner.class, Pet.class, nameFilter));
        try (var stream = query.getResultStream(Pet.class)) {
            assertEquals(5, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithAlias() {
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE \0.name LIKE \0""", select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), alias(Pet.class), param(nameFilter))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(5, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithInParams() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE p.name IN (\0)""", select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), param(List.of("Iggy", "Lucky", "Sly")))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(3, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithInParamsArray() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE p.name IN (\0)""", select(Pet.class), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), param(new Object[]{"Iggy", "Lucky", "Sly"}))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(3, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetImplicit() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0.name IN (\0)""", Pet.class, Pet.class, Pet.class, List.of("Iggy", "Lucky", "Sly"))).prepare();
             var stream = query.getResultStream(Pet.class)) {
            assertEquals(3, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @DbTable("pet")
    public record PetWithRefNonNullViolation(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Persist(updatable = false) @DbColumn("type_id") PetType petType,
            @Nonnull @FK Ref<Owner> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectRefNullViolation() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    SELECT \0
                    FROM \0
                      INNER JOIN \0 ON p.type_id = pt.id
                    WHERE p.name IN (\0)""", select(PetWithRefNonNullViolation.class), table(PetWithRefNonNullViolation.class, "p"), table(PetType.class, "pt"), param("Sly"))).prepare()) {
                query.getSingleResult(PetWithRefNonNullViolation.class);
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testVisitWithAlias() {
        @DbTable("visit")
        record Visit(
                int id,
                LocalDate visitDate,
                String description
        ) implements Data {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0.visit_date >= \0""", Visit.class, Visit.class, alias(Visit.class), param(date, TemporalType.DATE))).prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testVisitsByDate() {
        @DbTable("visit")
        record Visit(
                int id,
                LocalDate visitDate,
                String description
        ) implements Data {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE visit_date >= \0""", Visit.class, Visit.class, param(date, TemporalType.DATE))).prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testVisitsByCalendar() {
        @DbTable("visit")
        record CustomVisit(
                int id,
                LocalDate visitDate,
                String description
        ) implements Data {}
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2023, Calendar.JANUARY, 9, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE visit_date >= \0""", CustomVisit.class, CustomVisit.class, param(calendar, TemporalType.DATE))).prepare();
             var stream = query.getResultStream(CustomVisit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testMissingTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    SELECT \0
                    FROM visit""", Visit.class)).prepare()) {
                var stream = query.getResultStream(Visit.class);
                stream.toList();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDuplicateTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    SELECT \0
                    FROM \0
                      INNER JOIN \0 ON a.id = b.id""", Visit.class, table(Visit.class, "a"), table(Visit.class, "b"))).prepare()) {
                query.getResultList(Visit.class);
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDelete() {
        String expectedSql = """
                DELETE x
                FROM visit x
                WHERE x.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            try (var ignore = ORMTemplate.of(dataSource).query(raw("""
                    DELETE \0
                    FROM \0
                    WHERE \0""", Visit.class, from(Visit.class, "x", true), where(Visit.builder().id(1).build()))).prepare()) {
                Assertions.fail("Should not reach here");
            } catch (PersistenceException ignore) {
                // Not supported in H2.
            }
        });
    }

    @Test
    public void testDeleteWithAlias() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                DELETE FROM \0
                WHERE \0.id = \0""", Visit.class, Visit.class, 1)).prepare()) {
            query.executeUpdate();
        }
    }

    @Test
    public void testDeleteWithType() {
        String expectedSql = """
            DELETE v
            FROM visit v
            WHERE v.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            try (var ignore = ORMTemplate.of(dataSource).query(raw("""
                    DELETE \0
                    FROM \0
                    WHERE \0""", Visit.class, Visit.class, where(Visit.builder().id(1).build()))).prepare()) {
                Assertions.fail("Should not reach here");
            } catch (PersistenceException ignore) {
                // Delete statements with alias are supported by many databases, but not by H2.
            }
        });
    }

    @Test
    public void testDeleteWithWrongType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    DELETE \0
                    FROM \0
                    WHERE \0""", Visit.class, Pet.class, where(Pet.builder().id(1).build()))).prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDeleteWithTypeAndAlias() {
        String expectedSql = """
                DELETE f
                FROM visit f
                WHERE f.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            try (var ignore = ORMTemplate.of(dataSource).query(raw("""
                    DELETE \0
                    FROM \0
                    WHERE \0""", Visit.class, from(Visit.class, "f", false), where(Visit.builder().id(1).build()))).prepare()) {
                Assertions.fail("Should not reach here");
            } catch (PersistenceException ignore) {
                // Delete statements with alias are supported by many databases, but not by H2.
            }
        });
    }

    @Test
    public void testDeleteWithAutoJoin() {
        String expectedSql = """
            DELETE v
            FROM visit v
            INNER JOIN pet p ON v.pet_id = p.id
            WHERE p.owner_id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
            try (var ignore = ORMTemplate.of(dataSource).query(raw("""
                    DELETE \0
                    FROM \0
                    WHERE \0""", Visit.class, from(Visit.class, true), where(Owner.builder().id(1).build()))).prepare()) {
                Assertions.fail("Should not reach here");
            } catch (PersistenceException ignore) {
                // Not supported in H2.
            }
        });
    }

    @Test
    public void testSingleInsert() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                INSERT INTO \0
                VALUES \0""", Visit.class, values(visit))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON v.pet_id = p.id
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE v.description LIKE \0""", select(Visit.class), table(Visit.class, "v"), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), "test%")).prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(1, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testSingleInsertTypeMismatch() {
        var e = assertThrows(PersistenceException.class, () ->{
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    INSERT INTO \0
                    VALUES \0""", Visit.class, values(Pet.builder().id(1).build()))).prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSingleInsertMissingInsertType() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        var e = assertThrows(PersistenceException.class, () ->{
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    INSERT INTO visit
                    VALUES \0""", values(visit))).prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testMultiInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                INSERT INTO \0
                VALUES \0, \0""", insert(Visit.class), values(visit1), values(visit2))).prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON v.pet_id = p.id
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE v.description LIKE \0""", select(Visit.class), table(Visit.class, "v"), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), "test%")).prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testListInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                INSERT INTO \0
                VALUES \0""", Visit.class, values(visit1, visit2))).prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON v.pet_id = p.id
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE v.description LIKE \0""", select(Visit.class), table(Visit.class, "v"), table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), "test%")).prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testInsertInlined() {
        Owner owner = Owner.builder()
                .firstName("John")
                .lastName("Doe")
                .address(new Address("271 University Ave", City.builder().id(1).build()))
                .telephone("1234567890")
                .build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                INSERT INTO \0
                VALUES \0""", Owner.class, values(owner))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE first_name = \0""", Owner.class, Owner.class, owner.firstName())).prepare();
             var stream = query.getResultStream(Owner.class)) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testWith() {
        record FilteredPet(int id, @FK Owner owner) implements Data {}
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                WITH filtered_pet AS (
                  SELECT * FROM pet WHERE name LIKE \0
                )
                SELECT \0
                FROM \0
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id""", param(nameFilter), select(FilteredPet.class), table(FilteredPet.class, "p"), table(Owner.class, "o"), table(City.class, "c"))).prepare();
             var stream = query.getResultStream(FilteredPet.class)) {
            assertEquals(5, stream.map(FilteredPet::owner).filter(Objects::nonNull).map(Owner::firstName).distinct().count());
        }
    }

    @Test
    public void testWithoutInline() {
        record Owner(
                @PK Integer id,
                String firstName,
                String lastName,
                Address address,
                String telephone) implements Data {}
        record FilteredPet(int id, @FK Owner owner) implements Data {}
        String nameFilter = "%y%";
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                WITH filtered_pet AS (
                  SELECT * FROM pet WHERE name LIKE \0
                )
                SELECT \0
                FROM \0
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id""", param(nameFilter), select(FilteredPet.class), table(FilteredPet.class, "p"), table(Owner.class, "o"), table(City.class, "c"))).prepare();
             var stream = query.getResultStream(FilteredPet.class)) {
            assertEquals(5, stream.map(FilteredPet::owner).filter(Objects::nonNull).map(Owner::firstName).distinct().count());
        }
    }

    @Test
    public void testSelectCompoundPk() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0""", VetSpecialty.class, VetSpecialty.class)).prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectCompoundPkWithTables() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON vs.vet_id = v.id
                  INNER JOIN \0 ON vs.specialty_id = s.id""", VetSpecialty.class, table(VetSpecialty.class, "vs"), table(Vet.class, "v"), table(Specialty.class, "s"))).prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPk() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", VetSpecialty.class, VetSpecialty.class, where(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build()))).prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPks() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", VetSpecialty.class, VetSpecialty.class, where(List.of(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build(),
                VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(3).specialtyId(2).build()).build())))).prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(2, stream.count());
        }
    }

    @Test
    public void testSelectWherePkCompoundPks() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", VetSpecialty.class, VetSpecialty.class, where(List.of(
                VetSpecialtyPK.builder().vetId(2).specialtyId(1).build(),
                VetSpecialtyPK.builder().vetId(3).specialtyId(2).build())
        ))).prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(2, stream.count());
        }
    }

    @Builder(toBuilder = true)
    @DbTable("vet_specialty")
    public record VetSpecialtyRefPk(
            // PK does not reflect the database, but suffices for the test case.
            @PK(generation = NONE) @FK @DbColumn("vet_id") Ref<Vet> id,
            @Nonnull @FK Specialty specialty) implements Entity<Ref<Vet>> {
    }

    @Test
    public void testSelectWhereRefPk() {
        var list = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", VetSpecialtyRefPk.class, VetSpecialtyRefPk.class, where(Ref.of(Vet.builder().id(2).build()))))
            .getResultList(VetSpecialtyRefPk.class);
        assertEquals(1, list.size());
    }

    @Test
    public void testSelectWherePathPk() {
        var pets = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", Pet.class, Pet.class, where(Pet_.owner.id, BETWEEN, 1, 3)))
            .getResultList(Pet.class);
        assertEquals(4, pets.size());
        assertEquals(3, pets.stream().map(Pet::owner).filter(Objects::nonNull).map(Owner::id).distinct().count());
    }

    @Test
    public void testSelectWherePathRecord() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).getById(3);
        var pets = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", Pet.class, Pet.class, where(Pet_.owner, EQUALS, owner)))
            .getResultList(Pet.class);
        assertEquals(2, pets.size());
        assertEquals(Set.of(owner), pets.stream().map(Pet::owner).collect(toSet()));
    }

    @Test
    public void testSelectWhereNoPathForeignRecord() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).getById(3);
        var pets = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", Pet.class, Pet.class, where(owner)))
            .getResultList(Pet.class);
        assertEquals(2, pets.size());
        assertEquals(Set.of(owner), pets.stream().map(Pet::owner).collect(toSet()));
    }

    @Test
    public void testSelectWhereSubPathPk() {
        var owner = ORMTemplate.of(dataSource).entity(Owner.class).getById(3);
        var pets = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                WHERE \0""", Pet.class, Pet.class, where(Pet_.owner.id, EQUALS, owner.id())))
            .getResultList(Pet.class);
        assertEquals(2, pets.size());
        assertEquals(Set.of(owner), pets.stream().map(Pet::owner).collect(toSet()));
    }

    @Test
    public void testInsertCompoundPk() {
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON vs.vet_id = v.id
                  INNER JOIN \0 ON vs.specialty_id = s.id""", VetSpecialty.class, table(VetSpecialty.class, "vs"), table(Vet.class, "v"), table(Specialty.class, "s"))).prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().isPresent());
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().isPresent());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                INSERT INTO \0
                VALUES \0""", VetSpecialty.class, values(new VetSpecialty(new VetSpecialtyPK(1, 1))))).prepare()) {
            query.executeUpdate();
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON vs.vet_id = v.id
                  INNER JOIN \0 ON vs.specialty_id = s.id""", VetSpecialty.class, table(VetSpecialty.class, "vs"), table(Vet.class, "v"), table(Specialty.class, "s"))).prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertEquals("James", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().orElseThrow());
            assertEquals("radiology", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().orElseThrow());
        }
    }

    @Test
    public void testSingleUpdate() {
        var update = Pet.builder().id(1).build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET name = \0
                WHERE \0""", Pet.class, "Leona", where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM \0
                WHERE name = \0""", table(Pet.class), "Leona")).prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testSingleUpdateMetamodel() {
        var update = Pet.builder().id(1).build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET \0 = \0
                WHERE \0""", Pet.class, Pet_.name, "Leona", where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM \0
                WHERE \0 = \0""", Pet.class, Pet_.name, "Leona")).prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhere() {
        var update = new Pet(1, "Leona", LocalDate.now(), Ref.of(PetType.builder().id(1).build()), Owner.builder().id(1).build());
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET \0
                WHERE \0""", Pet.class, set(update), where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT COUNT(*)
                FROM \0
                WHERE name = \0""", table(Pet.class), "Leona")).prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhereWithAlias() {
        var update = petRepository.findById(1).toBuilder()
                .name("Leona")
                .type(Ref.of(PetType.builder().id(1).build()))
                .build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET \0
                WHERE \0""", update(Pet.class, "p"), set(update), where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE \0""", Pet.class, table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), where(update))).prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(0, result.type().id());
        }
    }

    @Builder
    @DbTable("pet")
    public record PetWithUpdatable(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Persist @DbColumn("type_id") PetType petType,
            @Nullable @FK Owner owner
    ) implements Data {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableType() {
        var update = PetWithUpdatable.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET \0
                WHERE \0""", update(PetWithUpdatable.class, "p"), set(update), where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE \0""", Pet.class, table(Pet.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), where(Pet.builder().id(1).build()))).prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(LocalDate.of(2020, 9, 7), result.birthDate());
            assertEquals(2, result.type().id());
        }
    }

    @Test
    public void testUpdateSetWhereWithAliasClash() {
        String expectedSql = """
                SELECT p.id, p.name, p.birth_date, p.type_id, p.owner_id, o1.first_name, o1.last_name, o1.address, o1.city_id, c1.name, o1.telephone, o1.version
                FROM pet p
                LEFT JOIN owner o1 ON p.owner_id = o1.id
                LEFT JOIN city c1 ON o1.city_id = c1.id
                INNER JOIN owner o ON p.owner_id = o.id
                INNER JOIN city c ON o.city_id = c.id
                WHERE p.id = ?""";
        observe(sql -> assertEquals(expectedSql, sql.statement()), () -> {
             try (var query = ORMTemplate.of(dataSource).query(raw("""
                    SELECT \0
                    FROM \0
                    INNER JOIN \0 ON \0.owner_id = o.id
                    INNER JOIN \0 ON o.city_id = c.id
                    WHERE \0""", Pet.class, Pet.class, table(Owner.class, "o"), Pet.class, table(City.class, "c"), where(Pet.builder().id(1).build()))).prepare()){
                var result = query.getSingleResult(Pet.class);
                assertEquals("Leo", result.name());
                assertEquals(0, result.type().id());
            }
        });
    }

    @Test
    public void testUpdateSetWhereWithAliasAndTypeMismatch() {
        var e = assertThrows(PersistenceException.class, () -> {
            var update = PetWithUpdatable.builder()
                    .id(1)
                    .name("Leona")
                    .petType(PetType.builder().id(2).build())
                    .owner(Owner.builder().id(1).build())
                    .build();
            try (var query = ORMTemplate.of(dataSource).query(raw("""
                    UPDATE \0
                    SET \0
                    WHERE \0""", update(Pet.class, "p"), set(update), where(update))).prepare()) {
                assertEquals(1, query.executeUpdate());
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder
    @DbTable("pet")
    public record PetWithoutPersist(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @DbColumn("type_id") PetType petType,
            @Nullable @FK Owner owner
    ) implements Data {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableTypeWithoutPersist() {
        var update = PetWithoutPersist.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                UPDATE \0
                SET \0
                WHERE \0""", update(PetWithoutPersist.class, "p"), set(update), where(update))).prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT \0
                FROM \0
                  INNER JOIN \0 ON p.type_id = pt.id
                  LEFT OUTER JOIN \0 ON p.owner_id = o.id
                  LEFT OUTER JOIN \0 ON o.city_id = c.id
                WHERE \0""", PetWithoutPersist.class, table(PetWithoutPersist.class, "p"), table(PetType.class, "pt"), table(Owner.class, "o"), table(City.class, "c"), where(update))).prepare()) {
            var result = query.getSingleResult(PetWithoutPersist.class);
            assertEquals("Leona", result.name());
            assertEquals(LocalDate.of(2020, 9, 7), result.birthDate());
            assertEquals(2, result.petType().id());
        }
    }

    @Test
    public void testSelect() {
        var count = ORMTemplate.of(dataSource).selectFrom(Visit.class).append(raw("WHERE \0.id = \0", alias(Visit.class), 1)).getResultCount();
        assertEquals(1, count);
    }

    @Test
    public void testCustomFrom() {
        var query = ORMTemplate.of(dataSource).query(raw("""
                SELECT a.*
                FROM \0
                """, from(TemplateString.of("SELECT * FROM visit"), "a")));
        assertEquals(14, query.getResultCount());
    }

    @Test
    public void testCustomTemplate() {
        var query = ORMTemplate.of(dataSource).query(TemplateBuilder.create(it -> """
                SELECT %s
                FROM %s
                """.formatted(it.insert(Pet.class), it.insert(Pet.class))));
        assertEquals(13, query.getResultCount());
    }

    @Test
    public void testCustomTemplateExists() {
        var orm = ORMTemplate.of(dataSource);
        var query = orm.query(TemplateBuilder.create(it -> """
                SELECT %s
                FROM %s
                WHERE EXISTS (%s)
                """.formatted(it.insert(Pet.class), it.insert(Pet.class), it.insert(orm.subquery(Pet.class)
                .where(TemplateBuilder.create(i -> "%s.id = %s.id".formatted(i.insert(alias(Pet.class, OUTER)), i.insert(alias(Pet.class, INNER)))))))));
        assertEquals(13, query.getResultCount());
    }

    @Test
    public void testCustomTemplateNotExists() {
        var orm = ORMTemplate.of(dataSource);
        var query = orm.query(TemplateBuilder.create(it -> """
                SELECT %s
                FROM %s
                WHERE NOT EXISTS (%s)
                """.formatted(it.insert(Pet.class), it.insert(Pet.class), it.insert(orm.subquery(Pet.class)
                .where(TemplateBuilder.create(i -> "%s.id = %s.id".formatted(i.insert(alias(Pet.class, OUTER)), i.insert(alias(Pet.class, INNER)))))))));
        assertEquals(0, query.getResultCount());
    }
}