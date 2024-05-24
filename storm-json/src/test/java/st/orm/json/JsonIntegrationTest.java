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
import st.orm.Name;
import st.orm.PK;
import st.orm.json.model.Address;
import st.orm.json.model.Owner;
import st.orm.json.model.Specialty;
import st.orm.json.model.Vet;
import st.orm.json.model.VetSpecialty;
import st.orm.repository.Entity;

import javax.sql.DataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static st.orm.Templates.ORM;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class JsonIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSelectOwner() {
        var ORM = ORM(dataSource);
        var query = ORM."""
                SELECT id, first_name, last_name, address, telephone FROM owner""";
        var owner = query.getResultList(Owner.class);
        assertEquals(10, owner.size());
    }

    @Test
    public void testInsertOwner() {
        var ORM = ORM(dataSource);
        var repository = ORM.repository(Owner.class);
        var address = new Address("271 University Ave", "Palo Alto");
        var owner = Owner.builder().firstName("Simon").lastName("McDonald").address(address).telephone("555-555-5555").build();
        var inserted = repository.insertAndFetch(owner.toBuilder().address(address).build());
        assertEquals(address, inserted.address());
    }

    @Test
    public void testUpdateOwner() {
        var ORM = ORM(dataSource);
        var repository = ORM.repository(Owner.class);
        var owner = repository.select(1);
        var address = new Address("271 University Ave", "Palo Alto");
        repository.update(owner.toBuilder().address(address).build());
        var updated = repository.select(1);
        assertEquals(address, updated.address());
    }

    public record Person(String firstName, String lastName) {}

    @Builder(toBuilder = true)
    @Name("owner")
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
        var query = ORM."""
                SELECT id, JSON_OBJECT('firstName' VALUE first_name, 'lastName' VALUE last_name) AS person, address, telephone FROM owner""";
        var owner = query.getResultList(OwnerWithJsonPerson.class);
        assertEquals(10, owner.size());
    }

    @Builder(toBuilder = true)
    @Name("owner")
    public record OwnerWithJsonMapAddress(
            @PK Integer id,
            @Nonnull @Name("first_name") String firstName,
            @Nonnull @Name("last_name") String lastName,
            @Nonnull @Json Map<String, String> address,
            @Nullable String telephone
    ) implements Entity<Integer> {
    }

    @Test
    public void testOwnerWithJsonMapAddress() {
        var ORM = ORM(dataSource);
        var repository = ORM.repository(OwnerWithJsonMapAddress.class);
        var owner = repository.selectAll().toList();
        assertEquals(10, owner.size());
    }

    public record VetWithSpecialties(@Nonnull Vet vet, @Nonnull @Json List<Specialty> specialties) {}

    @Test
    public void testVetWithSpecialties() {
        var ORM = ORM(dataSource);
        var vets = ORM.query(Vet.class)
                .selectTemplate(VetWithSpecialties.class)."""
                        \{Vet.class}, JSON_ARRAYAGG(
                            JSON_OBJECT(
                                KEY 'id' VALUE \{ORM.a(Specialty.class)}.id,
                                KEY 'name' VALUE \{ORM.a(Specialty.class)}.name
                            )
                        ) AS specialties"""
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                ."GROUP BY \{Vet.class}.id"
                .toList();
        assertEquals(4, vets.size());
        assertEquals(5, vets.stream().flatMap(v -> v.specialties().stream()).count());
    }

    public record VetWithSpecialtyList(@Nonnull Vet vet, @Nonnull @Json List<String> specialties) {}

    @Test
    public void testVetWithSpecialtyList() {
        var ORM = ORM(dataSource);
        var vets = ORM.query(Vet.class)
                .selectTemplate(VetWithSpecialtyList.class)."""
                        \{Vet.class}, JSON_ARRAYAGG(\{ORM.a(Specialty.class)}.name) AS specialties"""
                .innerJoin(VetSpecialty.class).on(Vet.class)
                .innerJoin(Specialty.class).on(VetSpecialty.class)
                ."GROUP BY \{Vet.class}.id"
                .toList();
        assertEquals(4, vets.size());
        assertEquals(5, vets.stream().flatMap(v -> v.specialties().stream()).count());
    }
}