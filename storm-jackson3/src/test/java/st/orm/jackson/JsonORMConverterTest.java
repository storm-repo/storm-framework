package st.orm.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.core.template.ORMTemplate.of;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.sql.DataSource;
import lombok.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.Json;
import st.orm.PK;
import st.orm.PersistenceException;
import st.orm.jackson.model.Address;
import st.orm.jackson.model.Owner;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class JsonORMConverterTest {

    @Autowired
    private DataSource dataSource;

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithFailOnUnknown(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json(failOnUnknown = true) Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithFailOnMissing(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json(failOnMissing = true) Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithBothFailOptions(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nonnull @Json(failOnUnknown = true, failOnMissing = true) Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("owner")
    public record OwnerWithNullableAddress(
            @PK Integer id,
            @Nonnull String firstName,
            @Nonnull String lastName,
            @Nullable @Json Address address,
            @Nullable String telephone
    ) implements Entity<Integer> {}

    @Test
    public void failOnUnknownTrueShouldStillWorkWithValidJson() {
        var orm = of(dataSource);
        var owner = orm.entity(OwnerWithFailOnUnknown.class).getById(1);
        assertNotNull(owner.address());
        assertEquals("638 Cardinal Ave.", owner.address().address());
    }

    @Test
    public void failOnMissingTrueShouldStillWorkWithCompleteJson() {
        var orm = of(dataSource);
        var owner = orm.entity(OwnerWithFailOnMissing.class).getById(1);
        assertNotNull(owner.address());
        assertEquals("638 Cardinal Ave.", owner.address().address());
    }

    @Test
    public void failOnUnknownAndFailOnMissingBothTrueShouldWork() {
        var orm = of(dataSource);
        var owner = orm.entity(OwnerWithBothFailOptions.class).getById(1);
        assertNotNull(owner.address());
        assertEquals("638 Cardinal Ave.", owner.address().address());
    }

    @Test
    public void insertOwnerShouldSerializeJsonAddressToDatabase() {
        var orm = of(dataSource);
        var repository = orm.entity(Owner.class);
        var address = new Address("271 University Ave", "Palo Alto");
        var owner = Owner.builder()
                .firstName("Simon")
                .lastName("McDonald")
                .address(address)
                .telephone("555-555-5555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertNotNull(inserted.address());
        assertEquals("271 University Ave", inserted.address().address());
        assertEquals("Palo Alto", inserted.address().city());
    }

    @Test
    public void toDatabaseWithNullJsonFieldShouldProduceNull() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithNullableAddress.class);
        var owner = OwnerWithNullableAddress.builder()
                .firstName("Test")
                .lastName("NullAddress")
                .address(null)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertNull(inserted.address());
    }

    @Test
    public void fromDatabaseWithNullJsonValueShouldReturnNullForNullableField() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithNullableAddress.class);
        var owner = OwnerWithNullableAddress.builder()
                .firstName("Null")
                .lastName("Address")
                .address(null)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertNull(inserted.address());
    }

    @Test
    public void fromDatabaseWithInvalidJsonShouldThrowException() {
        var orm = of(dataSource);
        var query = orm.query("SELECT id, first_name, last_name, 'not-valid-json' AS address, telephone FROM owner WHERE id = 1");
        assertThrows(PersistenceException.class,
                () -> query.getSingleResult(OwnerWithFailOnUnknown.class));
    }

    @Test
    public void selectAllOwnersWithFailOnUnknownShouldReturnAll() {
        var orm = of(dataSource);
        var owners = orm.entity(OwnerWithFailOnUnknown.class).select().getResultList();
        assertEquals(10, owners.size());
    }

    @Test
    public void selectAllOwnersWithFailOnMissingShouldReturnAll() {
        var orm = of(dataSource);
        var owners = orm.entity(OwnerWithFailOnMissing.class).select().getResultList();
        assertEquals(10, owners.size());
    }

    @Test
    public void updateOwnerJsonFieldShouldPersistChanges() {
        var orm = of(dataSource);
        var repository = orm.entity(Owner.class);
        var owner = repository.getById(1);
        var newAddress = new Address("100 Main St", "Springfield");
        repository.update(owner.toBuilder().address(newAddress).build());
        var updated = repository.getById(1);
        assertEquals("100 Main St", updated.address().address());
        assertEquals("Springfield", updated.address().city());
    }

    @Test
    public void updateOwnerNullableAddressToNullAndBackShouldRoundTrip() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithNullableAddress.class);
        var address = new Address("test", "city");
        var owner = OwnerWithNullableAddress.builder()
                .firstName("Round")
                .lastName("Trip")
                .address(address)
                .telephone("555")
                .build();
        var inserted = repository.insertAndFetch(owner);
        assertNotNull(inserted.address());

        // Update to null address.
        repository.update(inserted.toBuilder().address(null).build());
        var withNullAddress = repository.getById(inserted.id());
        assertNull(withNullAddress.address());

        // Update back to non-null address.
        repository.update(withNullAddress.toBuilder().address(new Address("new", "addr")).build());
        var restored = repository.getById(inserted.id());
        assertNotNull(restored.address());
        assertEquals("new", restored.address().address());
    }

    @Test
    public void selectNullableAddressWithNullValueShouldReturnNull() {
        var orm = of(dataSource);
        var repository = orm.entity(OwnerWithNullableAddress.class);
        var owner = OwnerWithNullableAddress.builder()
                .firstName("Direct")
                .lastName("Null")
                .address(null)
                .telephone("555")
                .build();
        repository.insert(owner);

        var allOwners = repository.select().getResultList();
        long nullAddressCount = allOwners.stream().filter(o -> o.address() == null).count();
        assertTrue(nullAddressCount >= 1);
    }
}
