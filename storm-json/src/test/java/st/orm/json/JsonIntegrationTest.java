package st.orm.json;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.Inline;
import st.orm.DbName;
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

import static java.lang.StringTemplate.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static st.orm.Templates.ORM;
import static st.orm.Templates.alias;

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
        var owner = repository.select(1);
        var address = new Address("271 University Ave", "Palo Alto");
        repository.update(owner.toBuilder().address(address).build());
        var updated = repository.select(1);
        assertEquals(address, updated.address());
    }

    public record Person(String firstName, String lastName) {}

    @Builder(toBuilder = true)
    @DbName("owner")
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
    @DbName("owner")
    public record OwnerWithJsonMapAddress(
            @PK Integer id,
            @Nonnull @DbName("first_name") String firstName,
            @Nonnull @DbName("last_name") String lastName,
            @Nonnull @Json java.util.Map<String, String> address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithJsonMapAddress() {
        var ORM = ORM(dataSource);
        var repository = ORM.entity(OwnerWithJsonMapAddress.class);
        var owner = repository.selectAll();
        assertEquals(10, owner.count());
    }

    @Builder(toBuilder = true)
    @DbName("owner")
    public record OwnerWithInlineJsonMapAddress(
            @PK Integer id,
            @Nonnull @DbName("first_name") String firstName,
            @Nonnull @DbName("last_name") String lastName,
            @Nonnull @Inline @Json java.util.Map<String, String> address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithInlineJsonMapAddress() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            var ORM = ORM(dataSource);
            var repository = ORM.entity(OwnerWithInlineJsonMapAddress.class);
            repository.selectAll();
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
}