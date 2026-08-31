package st.orm.spi.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static st.orm.GenerationStrategy.SEQUENCE;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.orm.DbTable;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Version;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.SqlInterceptor;
import st.orm.test.StormTest;

/**
 * Write-set tests that require a dialect with native upsert support; the H2 dialect maps upsert to {@code MERGE}.
 * Also covers generated-key propagation for sequence-based primary keys.
 */
@StormTest(scripts = "/data.sql")
public class H2WriteSetTest {

    private DataSource dataSource;

    @BeforeEach
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Builder(toBuilder = true)
    public record Owner(
            @PK Integer id,
            String firstName,
            String lastName,
            @Nullable String telephone,
            @Version int version
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    public record PetType(
            @PK Integer id,
            String name
    ) implements Entity<Integer> {}

    @Builder(toBuilder = true)
    @DbTable("pet")
    public record Pet(
            @PK(generation = SEQUENCE, sequence = "pet_id_seq") Integer id,
            String name,
            LocalDate birthDate,
            @FK PetType type,
            @Nullable @FK Owner owner
    ) implements Entity<Integer> {}

    @Test
    public void testInsertGraphPropagatesKeysToSequenceBasedLeaf() {
        // The pet's own sequence-generated key is consumed by nobody, so the write set inserts it without fetch
        // mode, which H2 does not support for sequence-based keys. The identity-based owner and pet type keys are
        // consumed by the pet and are fetched and propagated.
        var orm = ORMTemplate.of(dataSource);
        var owner = Owner.builder().firstName("Seq").lastName("Owner").build();
        var type = PetType.builder().name("writeSetDog").build();
        var pet = Pet.builder().name("SeqPet").birthDate(LocalDate.of(2024, 4, 4)).type(type).owner(owner).build();
        orm.writeSet().insert(List.of(pet));
        var fetched = orm.entity(Pet.class).select().getResultList().stream()
                .filter(candidate -> "SeqPet".equals(candidate.name()))
                .toList();
        assertEquals(1, fetched.size());
        assertNotEquals(0, fetched.getFirst().id());
        assertNotNull(fetched.getFirst().owner());
        assertNotEquals(0, fetched.getFirst().owner().id());
        assertEquals("Seq", fetched.getFirst().owner().firstName());
        assertEquals("writeSetDog", fetched.getFirst().type().name());
    }

    @Test
    public void testUpsertWritesExplicitKeyedParentBeforeReferencingChild() {
        // Explicit membership takes precedence over being referenced: the keyed pet type is upserted, not
        // bind-only, and it is written before the pet that references it, even though the pet appears first in
        // the list. Upsert preserves the member's key, so the ordering applies to auto-generated keys as well.
        var orm = ORMTemplate.of(dataSource);
        var existingType = orm.entity(PetType.class).insertAndFetch(PetType.builder().name("orderedType").build());
        var renamedType = existingType.toBuilder().name("orderedTypeRenamed").build();
        var pet = Pet.builder().name("OrderedPet").birthDate(LocalDate.of(2022, 2, 2)).type(renamedType).build();
        List<String> statements = new ArrayList<>();
        SqlInterceptor.observe(
                sql -> statements.add(sql.statement().toLowerCase()),
                () -> { orm.writeSet().upsert(List.of(pet, renamedType)); return null; });
        assertEquals("orderedTypeRenamed", orm.entity(PetType.class).getById(existingType.id()).name());
        var fetched = orm.entity(Pet.class).select().getResultList().stream()
                .filter(candidate -> "OrderedPet".equals(candidate.name()))
                .toList();
        assertEquals(1, fetched.size());
        assertEquals(existingType.id(), fetched.getFirst().type().id());
        var petTypeIndex = indexOfFirst(statements, statement -> statement.contains("pet_type"));
        var petIndex = indexOfFirst(statements, statement -> statement.contains("into pet ") || statement.contains("into pet\n"));
        assertTrue(petTypeIndex >= 0 && petIndex >= 0, String.join("\n", statements));
        assertTrue(petTypeIndex < petIndex, String.join("\n", statements));
    }

    private static int indexOfFirst(List<String> statements, java.util.function.Predicate<String> predicate) {
        for (int i = 0; i < statements.size(); i++) {
            if (predicate.test(statements.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void testUpsertMixesInsertAndUpdateBranches() {
        var orm = ORMTemplate.of(dataSource);
        var dogType = orm.entity(PetType.class).insertAndFetch(PetType.builder().name("writeSetDog").build());
        var newOwner = Owner.builder().firstName("Upsert").lastName("Fresh").build();
        var pet = Pet.builder().name("Upserted").birthDate(LocalDate.of(2019, 9, 9)).type(dogType).owner(newOwner).build();
        // One call: the keyed pet type takes the update branch, the pet takes the insert branch, and the unsaved
        // owner joins via insert discovery and is inserted first.
        orm.writeSet().upsert(List.of(dogType.toBuilder().name("writeSetDoggo").build(), pet));
        assertEquals("writeSetDoggo", orm.entity(PetType.class).getById(dogType.id()).name());
        var fetched = orm.entity(Pet.class).select().getResultList().stream()
                .filter(candidate -> "Upserted".equals(candidate.name()))
                .toList();
        assertEquals(1, fetched.size());
        assertNotNull(fetched.getFirst().owner());
        assertEquals("Upsert", fetched.getFirst().owner().firstName());
        assertEquals(dogType.id(), fetched.getFirst().type().id());
    }
}
