package st.orm;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TemporalType;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.model.Address;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Specialty;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.VetSpecialtyPK;
import st.orm.model.Visit;
import st.orm.repository.Entity;
import st.orm.repository.spring.PetRepository;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.sql.SQLSyntaxErrorException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Stream;

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Templates.ORM;
import static st.orm.Templates.from;

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
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{Pet.class}
                FROM \{Pet.class}""".prepare()) {
            var stream = query.getResultStream(Pet.class);
            assertEquals(10, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithJoins() {
        String nameFilter = "%y%";
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name LIKE \{ORM.p(nameFilter)}""".prepare()) {
            var stream = query.getResultStream(Pet.class);
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
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{ORM.a(Pet.class)}.name LIKE \{ORM.p(nameFilter)}""".prepare()) {
            var stream = query.getResultStream(Pet.class);
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
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name IN (\{ORM.p(List.of("Iggy", "Lucky", "Sly"))})""".prepare()) {
            var stream = query.getResultStream(Pet.class);
            assertEquals(3, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Test
    public void testSelectPetWithInParamsDirect() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{ORM.s(Pet.class)}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name IN (\{ORM.p(List.of("Iggy", "Lucky", "Sly"))})""".prepare()) {
            var stream = query.getResultStream(Pet.class);
            assertEquals(3, stream.filter(Objects::nonNull)
                    .map(Pet::owner)
                    .filter(Objects::nonNull)
                    .map(Owner::firstName)
                    .distinct()
                    .count());
        }
    }

    @Name("pet")
    public record PetWithLazyNonNullViolation(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Name("type_id") @Persist(updatable = false) PetType petType,
            @Nonnull @FK @Name("owner_id") Lazy<Owner> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectLazyNullViolation() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    SELECT \{ORM.s(PetWithLazyNonNullViolation.class)}
                    FROM \{ORM.t(PetWithLazyNonNullViolation.class, "p")}
                      INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                    WHERE p.name IN (\{ORM.p(List.of("Sly"))})""".prepare()) {
                query.getSingleResult(PetWithLazyNonNullViolation.class);
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }


    @Test
    public void testVisitWithAlias() {
        @Name("visit")
        record Visit(
                int id,
                @Name("visit_date") LocalDate visitDate,
                String description
        ) {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{Visit.class}
                FROM \{Visit.class}
                WHERE \{ORM.a(Visit.class)}.visit_date >= \{ORM.p(date, TemporalType.DATE)}""".prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testVisitsByDate() {
        @Name("visit")
        record Visit(
                int id,
                @Name("visit_date") LocalDate visitDate,
                String description
        ) {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{Visit.class}
                FROM \{Visit.class}
                WHERE visit_date >= \{ORM.p(date, TemporalType.DATE)}""".prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testVisitsByCalendar() {
        @Name("visit")
        record CustomVisit(
                int id,
                @Name("visit_date")LocalDate visitDate,
                String description
        ) {}
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2023, Calendar.JANUARY, 9, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{CustomVisit.class}
                FROM \{CustomVisit.class}
                WHERE visit_date >= \{ORM.p(calendar, TemporalType.DATE)}""".prepare();
             var stream = query.getResultStream(CustomVisit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testMissingTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    SELECT \{Visit.class}
                    FROM visit""".prepare()) {
                var stream = query.getResultStream(Visit.class);
                stream.toList();
            }
        });
        assertInstanceOf(SQLSyntaxErrorException.class, e.getCause());
    }

    @Test
    public void testDuplicateTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    SELECT \{Visit.class}
                    FROM \{ORM.t(Visit.class, "a")}
                      INNER JOIN \{ORM.t(Visit.class, "b")} ON a.id = b.id""".prepare()) {
                var stream = query.getResultStream(Visit.class);
                stream.toList();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDelete() {
        try {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                DELETE \{Visit.class}
                FROM \{ORM.f(Visit.class, "x")}
                WHERE \{ORM.w(Visit.builder().id(1).build())}""".prepare()) {
                query.executeUpdate();
            }
        } catch (PersistenceException e) {
            // Not supported in H2.
        }
    }

    @Test
    public void testDeleteWithAlias() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
            DELETE FROM \{Visit.class}
            WHERE \{Visit.class}.id = \{1}""".prepare()) {
            query.executeUpdate();
        }
    }

    @Test
    public void testDeleteWithType() {
        // Delete statements with alias are supported by many databases, but not by H2.
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    DELETE \{Visit.class}
                    FROM \{Visit.class}
                    WHERE \{ORM.w(Visit.builder().id(1).build())}""".prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SQLSyntaxErrorException.class, e.getCause());
    }

    @Test
    public void testDeleteWithWrongType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    DELETE \{Visit.class}
                    FROM \{Pet.class}
                    WHERE \{ORM.w(Pet.builder().id(1).build())}""".prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDeleteWithTypeAndAlias() {
        // Delete statements with alias are supported by many databases, but not by H2.
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    DELETE \{Visit.class}
                    FROM \{ORM.f(Visit.class, "f")}
                    WHERE \{ORM.w(Visit.builder().id(1).build())}""".prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SQLSyntaxErrorException.class, e.getCause());
    }

    @Test
    public void testSingleInsert() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                INSERT INTO \{Visit.class}
                VALUES \{ORM.v(visit)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{ORM.s(Visit.class)}
                FROM \{ORM.t(Visit.class, "v")}
                  INNER JOIN \{ORM.t(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""".prepare()) {
            var stream = query.getResultStream(Visit.class);
            assertEquals(1, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testSingleInsertTypeMismatch() {
        var e = assertThrows(PersistenceException.class, () ->{
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    INSERT INTO \{Visit.class}
                    VALUES \{ORM.v(Pet.builder().id(1).build())}""".prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSingleInsertMissingInsertType() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        var e = assertThrows(PersistenceException.class, () ->{
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    INSERT INTO visit
                    VALUES \{ORM.v(visit)}""".prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testMultiInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                INSERT INTO \{ORM.i(Visit.class)}
                VALUES \{ORM.v(visit1)}, \{ORM.v(visit2)}""".prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{ORM.s(Visit.class)}
                FROM \{ORM.t(Visit.class, "v")}
                  INNER JOIN \{ORM.t(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""".prepare()) {
            var stream = query.getResultStream(Visit.class);
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testListInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                INSERT INTO \{Visit.class}
                VALUES \{ORM.v(Stream.of(visit1, visit2))}""".prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{ORM.s(Visit.class)}
                FROM \{ORM.t(Visit.class, "v")}
                  INNER JOIN \{ORM.t(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""".prepare()) {
            var stream = query.getResultStream(Visit.class);
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testInsertInlined() {
        Owner owner = Owner.builder().
                firstName("John").
                lastName("Doe").
                address(new Address("271 University Ave", "Palo Alto")).
                telephone("1234567890").
                build();
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                INSERT INTO \{Owner.class}
                VALUES \{ORM.v(owner)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{Owner.class}
                FROM \{Owner.class}
                WHERE first_name = \{owner.firstName()}""".prepare()) {
            var stream = query.getResultStream(Owner.class);
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testWith() {
        record FilteredPet(int id, Owner owner) {}
        String nameFilter = "%y%";
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                WITH FilteredPet AS (
                  SELECT * FROM pet WHERE name LIKE \{ORM.p(nameFilter)}
                )
                SELECT \{ORM.s(FilteredPet.class)}
                FROM \{ORM.t(FilteredPet.class, "p")}
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id""".prepare()) {
            var stream = query.getResultStream(FilteredPet.class);
            assertEquals(5, stream.map(FilteredPet::owner).filter(Objects::nonNull).map(Owner::firstName).distinct().count());
        }
    }

    @Test
    public void testSelectCompoundPk() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}""".prepare()) {
            var stream = query.getResultStream(VetSpecialty.class);
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectCompoundPkWithTables() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{ORM.t(VetSpecialty.class, "vs")}
                  INNER JOIN \{ORM.t(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{ORM.t(Specialty.class, "s")} ON vs.specialty_id = s.id""".prepare()) {
            var stream = query.getResultStream(VetSpecialty.class);
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPk() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{ORM.w(Stream.of(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build()))}""".prepare()) {
            var stream = query.getResultStream(VetSpecialty.class);
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPks() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{ORM.w(Stream.of(
                        VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build(),
                        VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(3).specialtyId(2).build()).build()
                ))}""".prepare()) {
            var stream = query.getResultStream(VetSpecialty.class);
            assertEquals(2, stream.count());
        }
    }

    @Test
    public void testSelectWherePkCompoundPks() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{ORM.w(Stream.of(
                        VetSpecialtyPK.builder().vetId(2).specialtyId(1).build(),
                        VetSpecialtyPK.builder().vetId(3).specialtyId(2).build()
                ))}""".prepare()) {
            var stream = query.getResultStream(VetSpecialty.class);
            assertEquals(2, stream.count());
        }
    }

    @Builder(toBuilder = true)
    @Name("vet_specialty")
    public record VetSpecialtyLazyPk(
            // PK does not reflect the database, but suffices for the test case.
            @PK @FK @Name("vet_id") Lazy<Vet> id,
            @Nonnull @FK @Name("specialty_id") Specialty specialty) implements Entity<Lazy<Vet>> {
    }

    @Test
    public void testSelectWhereLazyPk() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            try (var query = ORM."""
                    SELECT \{VetSpecialtyLazyPk.class}
                    FROM \{VetSpecialtyLazyPk.class}
                    WHERE \{ORM.w(Stream.of(Lazy.of(Vet.builder().id(1).build())))}""".prepare()) {
                query.getResultStream(VetSpecialtyLazyPk.class);
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testInsertCompoundPk() {
        var ORM = ORM(dataSource);
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{ORM.t(VetSpecialty.class, "vs")}
                  INNER JOIN \{ORM.t(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{ORM.t(Specialty.class, "s")} ON vs.specialty_id = s.id""".prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().isPresent());
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().isPresent());
        }
        try (var query = ORM."""
                INSERT INTO \{VetSpecialty.class}
                VALUES \{ORM.v(new VetSpecialty(new VetSpecialtyPK(1, 1)))}""".prepare()) {
            query.executeUpdate();
        }
        try (var query = ORM."""
                SELECT \{VetSpecialty.class}
                FROM \{ORM.t(VetSpecialty.class, "vs")}
                  INNER JOIN \{ORM.t(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{ORM.t(Specialty.class, "s")} ON vs.specialty_id = s.id""".prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertEquals("James", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().orElseThrow());
            assertEquals("radiology", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().orElseThrow());
        }
    }

    @Test
    public void testSingleUpdate() {
        var ORM = ORM(dataSource);
        var update = Pet.builder().id(1).build();
        try (var query = ORM."""
                UPDATE \{Pet.class}
                SET name = \{"Leona"}
                WHERE \{ORM.w(update)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT COUNT(*)
                FROM \{ORM.t(Pet.class)}
                WHERE name = \{"Leona"}""".prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhere() {
        var ORM = ORM(dataSource);
        var update = new Pet(1, "Leona", LocalDate.now(), PetType.builder().id(1).build(), Owner.builder().id(1).build());
        try (var query = ORM."""
                UPDATE \{Pet.class}
                SET \{ORM.st(update)}
                WHERE \{ORM.w(update)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT COUNT(*)
                FROM \{ORM.t(Pet.class)}
                WHERE name = \{"Leona"}""".prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhereWithAlias() {
        var ORM = ORM(dataSource);
        var update = petRepository.findById(1).toBuilder()
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .build();
        try (var query = ORM."""
                UPDATE \{ORM.u(Pet.class, "p")}
                SET \{ORM.st(update)}
                WHERE \{ORM.w(update)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{Pet.class}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{ORM.w(update)}""".prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(1, result.petType().id());
        }
    }

    @Builder
    @Name("pet")
    public record PetWithUpdatable(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Name("type_id") @Persist(updatable = true) PetType petType,
            @Nullable @FK @Name("owner_id") Owner owner
    ) {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableType() {
        var ORM = ORM(dataSource);
        var update = PetWithUpdatable.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORM."""
                UPDATE \{ORM.u(PetWithUpdatable.class, "p")}
                SET \{ORM.st(update)}
                WHERE \{ORM.w(update)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{Pet.class}
                FROM \{ORM.t(Pet.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{ORM.w(Pet.builder().id(1).build())}""".prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(2, result.petType().id());
        }
    }

    @Test
    public void testUpdateSetWhereWithAliasAndTypeMismatch() {
        var e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var update = PetWithUpdatable.builder()
                    .id(1)
                    .name("Leona")
                    .petType(PetType.builder().id(2).build())
                    .owner(Owner.builder().id(1).build())
                    .build();
            try (var query = ORM."""
                    UPDATE \{ORM.u(Pet.class, "p")}
                    SET \{ORM.st(update)}
                    WHERE \{ORM.w(update)}""".prepare()) {
                assertEquals(1, query.executeUpdate());
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Builder
    @Name("pet")
    public record PetWithoutPersist(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Name("birth_date") @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Name("type_id") PetType petType,
            @Nullable @FK @Name("owner_id") Owner owner
    ) {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableTypeWithoutPersist() {
        var ORM = ORM(dataSource);
        var update = PetWithoutPersist.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORM."""
                UPDATE \{ORM.u(PetWithoutPersist.class, "p")}
                SET \{ORM.st(update)}
                WHERE \{ORM.w(update)}""".prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM."""
                SELECT \{PetWithoutPersist.class}
                FROM \{ORM.t(PetWithoutPersist.class, "p")}
                  INNER JOIN \{ORM.t(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{ORM.t(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{ORM.w(update)}""".prepare()) {
            var result = query.getSingleResult(PetWithoutPersist.class);
            assertEquals("Leona", result.name());
            assertEquals(2, result.petType().id());
        }
    }

    @Test
    public void testSelect() {
        var ORM = ORM(dataSource);
        var count = ORM.query(Visit.class)."WHERE \{ORM.a(Visit.class)}.id = \{1}".count();
        assertEquals(1, count);
    }

    @Test
    public void testCustomFrom() {
        var ORM = ORM(dataSource);
        var query = ORM."""
                SELECT a.*
                FROM \{from(RAW."SELECT * FROM visit", "a")}
                """;
        assertEquals(14, query.getResultStream().count());
    }

    @Test
    public void testCustomTemplate() {
        var ORM = ORM(dataSource);
        var query = ORM.template(it -> STR."""
                SELECT \{it.arg(Pet.class)}
                FROM \{it.arg(Pet.class)}
                """);
        assertEquals(13, query.getResultStream().count());
    }
}