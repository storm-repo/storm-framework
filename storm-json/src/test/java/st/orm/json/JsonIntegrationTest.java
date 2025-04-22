package st.orm.json;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.Inline;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.json.model.Address;
import st.orm.json.model.Owner;
import st.orm.json.model.Specialty;
import st.orm.json.model.Vet;
import st.orm.json.model.VetSpecialty;
import st.orm.repository.Entity;
import st.orm.template.SqlTemplateException;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.Templates.ORM;
import static st.orm.Templates.alias;
import static st.orm.template.SqlInterceptor.consume;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class JsonIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelectOwner() {
        var ORM = ORM(dataSource);
        var query = ORM.query(RAW."SELECT id, first_name, last_name, address, telephone FROM owner");
        var owner = query.getResultList(Owner.class);
        assertEquals(10, owner.size());
    }

    @Test
    public void testInsertOwner() {
        var ORM = ORM(dataSource);
        var repository = ORM.entity(Owner.class);
        var address = new Address("271 University Ave", "Palo Alto");
        var owner = Owner.builder().firstName("Simon").lastName("McDonald").address(address).telephone("555-555-5555").build();
        var inserted = repository.insertAndFetch(owner.toBuilder().address(address).build());
        assertEquals(address, inserted.address());
    }

    @Test
    public void testUpdateOwner() {
        var ORM = ORM(dataSource);
        var repository = ORM.entity(Owner.class);
        var owner = repository.getById(1);
        var address = new Address("271 University Ave", "Palo Alto");
        repository.update(owner.toBuilder().address(address).build());
        var updated = repository.getById(1);
        assertEquals(address, updated.address());
    }

    public record Person(String firstName, String lastName) {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithJsonPerson(
            @PK Integer id,
            @Nonnull @Json Person person,
            @Nonnull @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithJsonPerson() {
        var ORM = ORM(dataSource);
        var query = ORM.query(RAW."SELECT id, JSON_OBJECT('firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner");
        var owner = query.getResultList(OwnerWithJsonPerson.class);
        assertEquals(10, owner.size());
    }

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithJsonMapAddress(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json Map<String, String> address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithJsonMapAddress() {
        var ORM = ORM(dataSource);
        var repository = ORM.entity(OwnerWithJsonMapAddress.class);
        var owner = repository.select().getResultList();
        assertEquals(10, owner.size());
    }

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithInlineJsonMapAddress(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Inline @Json Map<String, String> address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithInlineJsonMapAddress() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var repository = ORM.entity(OwnerWithInlineJsonMapAddress.class);
            repository.select().getResultList();
        });
        assertInstanceOf(SqlTemplateException.class, e.getCause());
    }

    public record SpecialtiesByVet(@Nonnull Vet vet, @Nonnull @Json List<Specialty> specialties) {}

    @Test
    public void testSpecialtiesByVet() {
        var vets = ORM(dataSource)
                .selectFrom(Vet.class, SpecialtiesByVet.class, RAW."""
                        \{Vet.class}, JSON_ARRAYAGG(
                            JSON_OBJECT(
                                KEY 'id' VALUE \{alias(Specialty.class)}.id,
                                KEY 'name' VALUE \{alias(Specialty.class)}.name
                            )
                        ) AS specialties""")
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .append(RAW."GROUP BY \{Vet.class}.id")
                .getResultList();
        assertEquals(4, vets.size());
        assertEquals(5, vets.stream().mapToLong(v -> v.specialties().size()).sum());
    }

    record SpecialtyNamesByVet(@Nonnull Vet vet, @Nonnull @Json List<String> specialties) {}

    @Test
    public void testSpecialtyNamesByVet() {
        var vets = ORM(dataSource).selectFrom(Vet.class, SpecialtyNamesByVet.class, RAW."""
                        \{Vet.class}, JSON_ARRAYAGG(\{Specialty.class}.name) AS specialties""")
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                .append(RAW."GROUP BY \{Vet.class}.id")
                .getResultList();
        assertEquals(4, vets.size());
        assertEquals(5, vets.stream().mapToLong(v -> v.specialties().size()).sum());
    }

    @Test
    public void testSpecialtiesByVetDoubleClass() {
        String expectedSql = """
                SELECT v.id, v.first_name, v.last_name, JSON_OBJECTAGG(s.id, s.name)
                FROM vet v
                INNER JOIN vet_specialty vs ON vs.vet_id = v.id
                INNER JOIN specialty s ON s.id = vs.specialty_id
                GROUP BY v.id""";
        try (var _ = consume(sql -> assertEquals(expectedSql, sql.statement()))) {
            ORM(dataSource).selectFrom(Vet.class, SpecialtyNamesByVet.class, RAW."""
                        \{Vet.class}, JSON_OBJECTAGG(\{Specialty.class})""")
                    .innerJoin(VetSpecialty.class).on(Vet.class)
                    .innerJoin(Specialty.class).on(VetSpecialty.class)
                    .append(RAW."GROUP BY \{Vet.class}.id")
                    .getResultList();
        } catch (PersistenceException _) {
            // H2 Does not support JSON_OBJECTAGG. We only check the expected SQL.
        }
    }

    // No need to specify the sub types here, as we're automatically registering the implementations of the sealed interface.

    // @JsonSubTypes({
    //         @JsonSubTypes.Type(value = PersonA.class, name = "A"),
    //         @JsonSubTypes.Type(value = PersonB.class, name = "B")
    // })

    @JsonTypeInfo(use = NAME)
    public sealed interface PolymorphicPerson permits PersonA, PersonB {}

    // The type name is automatically derived from the class name, so the annotation is not needed.

    // @JsonTypeName("A")
    public record PersonA(String firstName, String lastName) implements PolymorphicPerson {}

    // @JsonTypeName("B")
    public record PersonB(String firstName, String lastName) implements PolymorphicPerson {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithPolymorphicPerson(
            @PK Integer id,
            @Nonnull @Json PolymorphicPerson person,
            @Nonnull @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testPolymorphic() {
        var ORM = ORM(dataSource);
        var query = ORM.query(RAW."SELECT id, JSON_OBJECT('@type' VALUE 'PersonA', 'firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner");
        var owner = query.getResultList(OwnerWithPolymorphicPerson.class);
        assertEquals(10, owner.size());
        assertTrue(owner.stream().allMatch(x -> x.person instanceof PersonA));
    }
}