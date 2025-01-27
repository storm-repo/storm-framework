package st.orm;

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
import st.orm.model.Address;
import st.orm.model.Owner;
import st.orm.model.Pet;
import st.orm.model.PetType;
import st.orm.model.Pet_;
import st.orm.model.Specialty;
import st.orm.model.Vet;
import st.orm.model.VetSpecialty;
import st.orm.model.VetSpecialtyPK;
import st.orm.model.Visit;
import st.orm.repository.Entity;
import st.orm.repository.spring.PetRepository;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.sql.SQLException;
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

import static java.lang.StringTemplate.RAW;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Templates.ORM;
import static st.orm.Templates.alias;
import static st.orm.Templates.from;
import static st.orm.Templates.insert;
import static st.orm.Templates.param;
import static st.orm.Templates.select;
import static st.orm.Templates.set;
import static st.orm.Templates.table;
import static st.orm.Templates.update;
import static st.orm.Templates.values;
import static st.orm.Templates.where;
import static st.orm.template.Operator.BETWEEN;
import static st.orm.template.Operator.EQUALS;
import static st.orm.template.ResolveScope.INNER;
import static st.orm.template.ResolveScope.OUTER;
import static st.orm.template.SqlInterceptor.intercept;
import static st.orm.template.TemplateFunction.template;

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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}""").prepare();
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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{from(Pet.class, "p", true)}
                WHERE p.name LIKE \{param(nameFilter)}""").prepare();
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
    public void testSelectPetWithTableAndJoins() {
        String nameFilter = "%y%";
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name LIKE \{param(nameFilter)}""").prepare();
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
    public void testSelectPetWithRecord() throws SQLException  {
        record Owner(String firstName, String lastName, Address address, String telephone) {}
        record Pet(String name, LocalDate birthDate, Owner owner) {}
        String nameFilter = "%y%";
        var query = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                  LEFT OUTER JOIN \{Owner.class} ON \{Pet.class}.owner_id = \{Owner.class}.id
                WHERE \{Pet.class}.name LIKE \{nameFilter}""");
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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{alias(Pet.class)}.name LIKE \{param(nameFilter)}""").prepare();
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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name IN (\{param(List.of("Iggy", "Lucky", "Sly"))})""").prepare();
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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Pet.class)}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE p.name IN (\{param(new Object[]{"Iggy", "Lucky", "Sly"})})""").prepare();
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
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{Pet.class}.name IN (\{List.of("Iggy", "Lucky", "Sly")})""").prepare();
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
    public record PetWithLazyNonNullViolation(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Persist(updatable = false) @DbColumn("type_id") PetType petType,
            @Nonnull @FK Lazy<Owner, Integer> owner
    ) implements Entity<Integer> {}

    @Test
    public void testSelectLazyNullViolation() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORM(dataSource).query(RAW."""
                    SELECT \{select(PetWithLazyNonNullViolation.class)}
                    FROM \{table(PetWithLazyNonNullViolation.class, "p")}
                      INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                    WHERE p.name IN (\{param("Sly")})""").prepare()) {
                query.getSingleResult(PetWithLazyNonNullViolation.class);
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
        ) {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Visit.class}
                FROM \{Visit.class}
                WHERE \{alias(Visit.class)}.visit_date >= \{param(date, TemporalType.DATE)}""").prepare();
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
        ) {}
        LocalDate localDate = LocalDate.of(2023, 1, 9);
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.of("UTC"));
        Date date = Date.from(zonedDateTime.toInstant());
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Visit.class}
                FROM \{Visit.class}
                WHERE visit_date >= \{param(date, TemporalType.DATE)}""").prepare();
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
        ) {}
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2023, Calendar.JANUARY, 9, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{CustomVisit.class}
                FROM \{CustomVisit.class}
                WHERE visit_date >= \{param(calendar, TemporalType.DATE)}""").prepare();
             var stream = query.getResultStream(CustomVisit.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testMissingTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORM(dataSource).query(RAW."""
                    SELECT \{Visit.class}
                    FROM visit""").prepare()) {
                var stream = query.getResultStream(Visit.class);
                stream.toList();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testDuplicateTable() {
        var e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORM(dataSource).query(RAW."""
                    SELECT \{Visit.class}
                    FROM \{table(Visit.class, "a")}
                      INNER JOIN \{table(Visit.class, "b")} ON a.id = b.id""").prepare()) {
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
        try (var _ = intercept(sql -> assertEquals(expectedSql, sql.statement()));
             var _ = ORM(dataSource).query(RAW."""
                DELETE \{Visit.class}
                FROM \{from(Visit.class, "x", true)}
                WHERE \{where(Visit.builder().id(1).build())}""").prepare()) {
            Assertions.fail("Should not reach here");
        } catch (PersistenceException _) {
            // Not supported in H2.
        }
    }

    @Test
    public void testDeleteWithAlias() {
        try (var query = ORM(dataSource).query(RAW."""
                DELETE FROM \{Visit.class}
                WHERE \{Visit.class}.id = \{1}""").prepare()) {
            query.executeUpdate();
        }
    }

    @Test
    public void testDeleteWithType() {
        String expectedSql = """
            DELETE _v
            FROM visit _v
            WHERE _v.id = ?""";
        try (var _ = intercept(sql -> assertEquals(expectedSql, sql.statement()));
             var _ = ORM(dataSource).query(RAW."""
                DELETE \{Visit.class}
                FROM \{Visit.class}
                WHERE \{where(Visit.builder().id(1).build())}""").prepare()) {
            Assertions.fail("Should not reach here");
        } catch (PersistenceException _) {
            // Delete statements with alias are supported by many databases, but not by H2.
        }
    }

    @Test
    public void testDeleteWithWrongType() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            try (var query = ORM(dataSource).query(RAW."""
                    DELETE \{Visit.class}
                    FROM \{Pet.class}
                    WHERE \{where(Pet.builder().id(1).build())}""").prepare()) {
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
        try (var _ = intercept(sql -> assertEquals(expectedSql, sql.statement()));
             var query = ORM(dataSource).query(RAW."""
                DELETE \{Visit.class}
                FROM \{from(Visit.class, "f", false)}
                WHERE \{where(Visit.builder().id(1).build())}""").prepare()) {
            Assertions.fail("Should not reach here");
        } catch (PersistenceException _) {
            // Delete statements with alias are supported by many databases, but not by H2.
        }
    }

    @Test
    public void testDeleteWithAutoJoin() {
        String expectedSql = """
            DELETE _v
            FROM visit _v
            INNER JOIN pet _p ON _v.pet_id = _p.id
            WHERE _p.owner_id = ?""";
        try (var _ = intercept(sql -> assertEquals(expectedSql, sql.statement()));
             var _ = ORM(dataSource).query(RAW."""
                DELETE \{Visit.class}
                FROM \{from(Visit.class, true)}
                WHERE \{where(Owner.builder().id(1).build())}""").prepare()) {
            Assertions.fail("Should not reach here");
        } catch (PersistenceException _) {
            // Not supported in H2.
        }
    }

    @Test
    public void testSingleInsert() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        try (var query = ORM(dataSource).query(RAW."""
                INSERT INTO \{Visit.class}
                VALUES \{values(visit)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Visit.class)}
                FROM \{table(Visit.class, "v")}
                  INNER JOIN \{table(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""").prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(1, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testSingleInsertTypeMismatch() {
        var e = assertThrows(PersistenceException.class, () ->{
            try (var query = ORM(dataSource).query(RAW."""
                    INSERT INTO \{Visit.class}
                    VALUES \{values(Pet.builder().id(1).build())}""").prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSingleInsertMissingInsertType() {
        Visit visit = new Visit(LocalDate.now(), "test", Pet.builder().id(1).build());
        var e = assertThrows(PersistenceException.class, () ->{
            try (var query = ORM(dataSource).query(RAW."""
                    INSERT INTO visit
                    VALUES \{values(visit)}""").prepare()) {
                query.executeUpdate();
            }
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testMultiInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        try (var query = ORM(dataSource).query(RAW."""
                INSERT INTO \{insert(Visit.class)}
                VALUES \{values(visit1)}, \{values(visit2)}""").prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Visit.class)}
                FROM \{table(Visit.class, "v")}
                  INNER JOIN \{table(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""").prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testListInsert() {
        Visit visit1 = new Visit(LocalDate.now(), "test1", Pet.builder().id(1).build());
        Visit visit2 = new Visit(LocalDate.now(), "test2", Pet.builder().id(1).build());
        try (var query = ORM(dataSource).query(RAW."""
                INSERT INTO \{Visit.class}
                VALUES \{values(visit1, visit2)}""").prepare()) {
            assertEquals(2, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{select(Visit.class)}
                FROM \{table(Visit.class, "v")}
                  INNER JOIN \{table(Pet.class, "p")} ON v.pet_id = p.id
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE v.description LIKE \{"test%"}""").prepare();
             var stream = query.getResultStream(Visit.class)) {
            assertEquals(2, stream.map(Visit::description).distinct().count());
        }
    }

    @Test
    public void testInsertInlined() {
        Owner owner = Owner.builder()
                .firstName("John")
                .lastName("Doe")
                .address(new Address("271 University Ave", "Palo Alto"))
                .telephone("1234567890")
                .build();
        try (var query = ORM(dataSource).query(RAW."""
                INSERT INTO \{Owner.class}
                VALUES \{values(owner)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Owner.class}
                FROM \{Owner.class}
                WHERE first_name = \{owner.firstName()}""").prepare();
             var stream = query.getResultStream(Owner.class)) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testWith() {
        record FilteredPet(int id, @FK Owner owner) {}
        String nameFilter = "%y%";
        try (var query = ORM(dataSource).query(RAW."""
                WITH filtered_pet AS (
                  SELECT * FROM pet WHERE name LIKE \{param(nameFilter)}
                )
                SELECT \{select(FilteredPet.class)}
                FROM \{table(FilteredPet.class, "p")}
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id""").prepare();
             var stream = query.getResultStream(FilteredPet.class)) {
            assertEquals(5, stream.map(FilteredPet::owner).filter(Objects::nonNull).map(Owner::firstName).distinct().count());
        }
    }

    @Test
    public void testWithoutInline() {
        record Owner(
                Integer id,
                String firstName,
                String lastName,
                Address address,
                String telephone) {}
        record FilteredPet(int id, @FK Owner owner) {}
        String nameFilter = "%y%";
        try (var query = ORM(dataSource).query(RAW."""
                WITH filtered_pet AS (
                  SELECT * FROM pet WHERE name LIKE \{param(nameFilter)}
                )
                SELECT \{select(FilteredPet.class)}
                FROM \{table(FilteredPet.class, "p")}
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id""").prepare();
             var stream = query.getResultStream(FilteredPet.class)) {
            assertEquals(5, stream.map(FilteredPet::owner).filter(Objects::nonNull).map(Owner::firstName).distinct().count());
        }
    }

    @Test
    public void testSelectCompoundPk() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}""").prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectCompoundPkWithTables() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{table(VetSpecialty.class, "vs")}
                  INNER JOIN \{table(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{table(Specialty.class, "s")} ON vs.specialty_id = s.id""").prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(5, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPk() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{where(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build())}""").prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    public void testSelectWhereCompoundPks() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{where(List.of(VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(2).specialtyId(1).build()).build(),
                        VetSpecialty.builder().id(VetSpecialtyPK.builder().vetId(3).specialtyId(2).build()).build())
                )}""").prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(2, stream.count());
        }
    }

    @Test
    public void testSelectWherePkCompoundPks() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{VetSpecialty.class}
                WHERE \{where(List.of(
                        VetSpecialtyPK.builder().vetId(2).specialtyId(1).build(),
                        VetSpecialtyPK.builder().vetId(3).specialtyId(2).build())
                )}""").prepare();
             var stream = query.getResultStream(VetSpecialty.class)) {
            assertEquals(2, stream.count());
        }
    }

    @Builder(toBuilder = true)
    @DbTable("vet_specialty")
    public record VetSpecialtyLazyPk(
            // PK does not reflect the database, but suffices for the test case.
            @PK @FK @DbColumn("vet_id") Lazy<Vet, Integer> id,
            @Nonnull @FK Specialty specialty) implements Entity<Lazy<Vet, Integer>> {
    }

    @Test
    public void testSelectWhereLazyPk() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            ORM(dataSource).query(RAW."""
                    SELECT \{VetSpecialtyLazyPk.class}
                    FROM \{VetSpecialtyLazyPk.class}
                    WHERE \{where(Lazy.of(Vet.builder().id(1).build()))}""")
                .getResultStream(VetSpecialtyLazyPk.class);
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    @Test
    public void testSelectWherePathPk() {
        var pets = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{where(Pet_.owner.id, BETWEEN, 1, 3)}""")
            .getResultList(Pet.class);
        assertEquals(4, pets.size());
        assertEquals(3, pets.stream().map(Pet::owner).filter(Objects::nonNull).map(Owner::id).distinct().count());
    }

    @Test
    public void testSelectWherePathRecord() {
        var owner = ORM(dataSource).entity(Owner.class).select(3);
        var pets = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{where(Pet_.owner, EQUALS, owner)}""")
            .getResultList(Pet.class);
        assertEquals(2, pets.size());
        assertEquals(Set.of(owner), pets.stream().map(Pet::owner).collect(toSet()));
    }

    @Test
    public void testSelectWhereSubPathPk() {
        var owner = ORM(dataSource).entity(Owner.class).select(3);
        var pets = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{Pet.class}
                WHERE \{where(Pet_.owner.id, EQUALS, owner.id())}""")
            .getResultList(Pet.class);
        assertEquals(2, pets.size());
        assertEquals(Set.of(owner), pets.stream().map(Pet::owner).collect(toSet()));
    }

    @Test
    public void testInsertCompoundPk() {
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{table(VetSpecialty.class, "vs")}
                  INNER JOIN \{table(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{table(Specialty.class, "s")} ON vs.specialty_id = s.id""").prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().isPresent());
            assertFalse(list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().isPresent());
        }
        try (var query = ORM(dataSource).query(RAW."""
                INSERT INTO \{VetSpecialty.class}
                VALUES \{values(new VetSpecialty(new VetSpecialtyPK(1, 1)))}""").prepare()) {
            query.executeUpdate();
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{VetSpecialty.class}
                FROM \{table(VetSpecialty.class, "vs")}
                  INNER JOIN \{table(Vet.class, "v")} ON vs.vet_id = v.id
                  INNER JOIN \{table(Specialty.class, "s")} ON vs.specialty_id = s.id""").prepare()) {
            var list = query.getResultList(VetSpecialty.class);
            assertEquals("James", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::vet).map(Vet::firstName).findFirst().orElseThrow());
            assertEquals("radiology", list.stream().filter(vs -> vs.vet().id() == 1 && vs.specialty().id() == 1).map(VetSpecialty::specialty).map(Specialty::name).findFirst().orElseThrow());
        }
    }

    @Test
    public void testSingleUpdate() {
        var update = Pet.builder().id(1).build();
        try (var query = ORM(dataSource).query(RAW."""
                UPDATE \{Pet.class}
                SET name = \{"Leona"}
                WHERE \{where(update)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT COUNT(*)
                FROM \{table(Pet.class)}
                WHERE name = \{"Leona"}""").prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhere() {
        var update = new Pet(1, "Leona", LocalDate.now(), PetType.builder().id(1).build(), Owner.builder().id(1).build());
        try (var query = ORM(dataSource).query(RAW."""
                UPDATE \{Pet.class}
                SET \{set(update)}
                WHERE \{where(update)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT COUNT(*)
                FROM \{table(Pet.class)}
                WHERE name = \{"Leona"}""").prepare()) {
            assertEquals(1, query.getSingleResult(Long.class));
        }
    }

    @Test
    public void testUpdateSetWhereWithAlias() {
        var update = petRepository.findById(1).toBuilder()
                .name("Leona")
                .type(PetType.builder().id(2).build())
                .build();
        try (var query = ORM(dataSource).query(RAW."""
                UPDATE \{update(Pet.class, "p")}
                SET \{set(update)}
                WHERE \{where(update)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{where(update)}""").prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(1, result.type().id());
        }
    }

    @Builder
    @DbTable("pet")
    public record PetWithUpdatable(
            @PK Integer id,
            @Nonnull String name,
            @Nonnull @Persist(updatable = false) LocalDate birthDate,
            @Nonnull @FK @Persist(updatable = true) @DbColumn("type_id") PetType petType,
            @Nullable @FK Owner owner
    ) {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableType() {
        var update = PetWithUpdatable.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORM(dataSource).query(RAW."""
                UPDATE \{update(PetWithUpdatable.class, "p")}
                SET \{set(update)}
                WHERE \{where(update)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{Pet.class}
                FROM \{table(Pet.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{where(Pet.builder().id(1).build())}""").prepare()) {
            var result = query.getSingleResult(Pet.class);
            assertEquals("Leona", result.name());
            assertEquals(2, result.type().id());
        }
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
            try (var query = ORM(dataSource).query(RAW."""
                    UPDATE \{update(Pet.class, "p")}
                    SET \{set(update)}
                    WHERE \{where(update)}""").prepare()) {
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
    ) {}

    @Test
    public void testUpdateSetWhereWithAliasAndUpdatableTypeWithoutPersist() {
        var update = PetWithoutPersist.builder()
                .id(1)
                .name("Leona")
                .petType(PetType.builder().id(2).build())
                .owner(Owner.builder().id(1).build())
                .build();
        try (var query = ORM(dataSource).query(RAW."""
                UPDATE \{update(PetWithoutPersist.class, "p")}
                SET \{set(update)}
                WHERE \{where(update)}""").prepare()) {
            assertEquals(1, query.executeUpdate());
        }
        try (var query = ORM(dataSource).query(RAW."""
                SELECT \{PetWithoutPersist.class}
                FROM \{table(PetWithoutPersist.class, "p")}
                  INNER JOIN \{table(PetType.class, "pt")} ON p.type_id = pt.id
                  LEFT OUTER JOIN \{table(Owner.class, "o")} ON p.owner_id = o.id
                WHERE \{where(update)}""").prepare()) {
            var result = query.getSingleResult(PetWithoutPersist.class);
            assertEquals("Leona", result.name());
            assertEquals(2, result.petType().id());
        }
    }

    @Test
    public void testSelect() {
        var count = ORM(dataSource).selectFrom(Visit.class).append(RAW."WHERE \{alias(Visit.class)}.id = \{1}").getResultCount();
        assertEquals(1, count);
    }

    @Test
    public void testCustomFrom() {
        var query = ORM(dataSource).query(RAW."""
                SELECT a.*
                FROM \{from(RAW."SELECT * FROM visit", "a")}
                """);
        assertEquals(14, query.getResultCount());
    }

    @Test
    public void testCustomTemplate() {
        var query = ORM(dataSource).query(template(it -> STR."""
                SELECT \{it.invoke(Pet.class)}
                FROM \{it.invoke(Pet.class)}
                """));
        assertEquals(13, query.getResultCount());
    }

    @Test
    public void testCustomTemplateExists() {
        var orm = ORM(dataSource);
        var query = orm.query(template(it -> STR."""
                SELECT \{it.invoke(Pet.class)}
                FROM \{it.invoke(Pet.class)}
                WHERE EXISTS (\{it.invoke(orm.subquery(Pet.class)
                    .where(template(i -> STR."\{i.invoke(alias(Pet.class, OUTER))}.id = \{i.invoke(alias(Pet.class, INNER))}.id")))} )
                """));
        assertEquals(13, query.getResultCount());
    }

    @Test
    public void testCustomTemplateNotExists() {
        var orm = ORM(dataSource);
        var query = orm.query(template(it -> STR."""
                SELECT \{it.invoke(Pet.class)}
                FROM \{it.invoke(Pet.class)}
                WHERE NOT EXISTS (\{it.invoke(orm.subquery(Pet.class)
                    .where(template(i -> STR."\{i.invoke(alias(Pet.class, OUTER))}.id = \{i.invoke(alias(Pet.class, INNER))}.id")))} )
                """));
        assertEquals(0, query.getResultCount());
    }
}