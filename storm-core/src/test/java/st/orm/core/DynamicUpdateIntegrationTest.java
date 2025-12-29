package st.orm.core;

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
import st.orm.DynamicUpdate;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;
import st.orm.Version;
import st.orm.core.model.Pet;
import st.orm.core.template.ORMTemplate;
import st.orm.core.template.Sql;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static st.orm.DirtyCheck.VALUE;
import static st.orm.UpdateMode.ENTITY;
import static st.orm.UpdateMode.FIELD;
import static st.orm.UpdateMode.OFF;
import static st.orm.core.template.SqlInterceptor.observe;

@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class DynamicUpdateIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @DynamicUpdate(OFF)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitOffDefault(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    @DynamicUpdate(value = OFF, dirtyCheck = VALUE)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitOffValue(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    @DynamicUpdate(ENTITY)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitEntityDefault(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    @DynamicUpdate(value = ENTITY, dirtyCheck = VALUE)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitEntityValue(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    @DynamicUpdate(FIELD)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitFieldDefault(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    @DynamicUpdate(value = FIELD, dirtyCheck = VALUE)
    @Builder(toBuilder = true)
    @DbTable("visit")
    public record VisitFieldValue(
            @PK Integer id,
            @Nonnull LocalDate visitDate,
            @Nullable String description,
            @Nonnull @FK Pet pet,
            @Version Instant timestamp
    ) implements Entity<Integer> {}

    // ------------------------------------------------------------
    // Expected SQL
    // ------------------------------------------------------------

    private static final String FULL_UPDATE_SQL = """
            UPDATE visit
            SET visit_date = ?, description = ?, pet_id = ?, "timestamp" = CURRENT_TIMESTAMP
            WHERE (id = ? AND "timestamp" = ?)""";

    private static final String FIELD_UPDATE_PET_SQL = """
            UPDATE visit
            SET pet_id = ?, "timestamp" = CURRENT_TIMESTAMP
            WHERE (id = ? AND "timestamp" = ?)""";

    // ------------------------------------------------------------
    // OFF: always updates all columns, dirtyCheck irrelevant
    // ------------------------------------------------------------

    @Test
    void off_default_alwaysUpdates_evenIfNoChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitOffDefault.class);
        var visit = repo.getById(1);

        // No changes, but OFF must still update.
        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FULL_UPDATE_SQL, sql.statement());
    }

    @Test
    void off_value_alwaysUpdates_evenIfNoChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitOffValue.class);
        var visit = repo.getById(1);

        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FULL_UPDATE_SQL, sql.statement());
    }

    // ------------------------------------------------------------
    // ENTITY: full update SQL, dirty detection is per-column
    // DEFAULT = instance-based dirty check
    // VALUE   = semantic value-based dirty check
    // ------------------------------------------------------------

    @Test
    void entity_default_skipsUpdate_whenNoMappedColumnChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityDefault.class);
        var visit = repo.getById(1);

        // Same values + same references.
        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    @Test
    void entity_value_skipsUpdate_whenNoMappedColumnChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityValue.class);
        var visit = repo.getById(1);

        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    @Test
    void entity_default_updatesFullRow_whenAnyMappedColumnIsDirty_petIdChanged() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityDefault.class);
        var visit = repo.getById(1);

        var pet2 = ORMTemplate.of(dataSource).entity(Pet.class).getById(2);
        var update = visit.toBuilder().pet(pet2).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FULL_UPDATE_SQL, sql.statement());
    }

    @Test
    void entity_value_updatesFullRow_whenAnyMappedColumnIsDirty_petIdChanged() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityValue.class);
        var visit = repo.getById(1);

        var pet2 = ORMTemplate.of(dataSource).entity(Pet.class).getById(2);
        var update = visit.toBuilder().pet(pet2).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FULL_UPDATE_SQL, sql.statement());
    }

    @Test
    void entity_default_updatesFullRow_whenFkInstanceChangesEvenIfIdSame() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityDefault.class);
        var visit = repo.getById(1);

        // Same FK id, different instance => DEFAULT(instance) considers it dirty.
        var petSameIdDifferentInstance = visit.pet().toBuilder().name("Simon").build();
        var update = visit.toBuilder().pet(petSameIdDifferentInstance).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FULL_UPDATE_SQL, sql.statement());
    }

    @Test
    void entity_value_skipsUpdate_whenFkIdSameEvenIfInstanceChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitEntityValue.class);
        var visit = repo.getById(1);

        var petSameIdDifferentInstance = visit.pet().toBuilder().name("Simon").build();
        var update = visit.toBuilder().pet(petSameIdDifferentInstance).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    // ------------------------------------------------------------
    // FIELD: updates only changed columns (plus version)
    // DEFAULT = instance-based dirty check
    // VALUE   = semantic value-based dirty check
    // ------------------------------------------------------------

    @Test
    void field_default_skipsUpdate_whenNoMappedColumnChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldDefault.class);
        var visit = repo.getById(1);

        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    @Test
    void field_value_skipsUpdate_whenNoMappedColumnChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldValue.class);
        var visit = repo.getById(1);

        var update = visit.toBuilder().build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    @Test
    void field_default_updatesOnlyChangedColumn_petIdChange() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldDefault.class);
        var visit = repo.getById(1);

        var pet2 = ORMTemplate.of(dataSource).entity(Pet.class).getById(2);
        var update = visit.toBuilder().pet(pet2).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FIELD_UPDATE_PET_SQL, sql.statement());
    }

    @Test
    void field_value_updatesOnlyChangedColumn_petIdChange() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldValue.class);
        var visit = repo.getById(1);

        var pet2 = ORMTemplate.of(dataSource).entity(Pet.class).getById(2);
        var update = visit.toBuilder().pet(pet2).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FIELD_UPDATE_PET_SQL, sql.statement());
    }

    @Test
    void field_default_updates_whenFkInstanceChangesEvenIfIdSame() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldDefault.class);
        var visit = repo.getById(1);

        // Same FK id, different instance => DEFAULT(instance) considers it dirty,
        // so Storm updates the FK column even though its value is the same.
        var petSameIdDifferentInstance = visit.pet().toBuilder().name("Simon").build();
        var update = visit.toBuilder().pet(petSameIdDifferentInstance).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNotNull(sql);
        assertEquals(FIELD_UPDATE_PET_SQL, sql.statement());
    }

    @Test
    void field_value_skipsUpdate_whenFkIdSameEvenIfInstanceChanges() {
        var repo = ORMTemplate.of(dataSource).entity(VisitFieldValue.class);
        var visit = repo.getById(1);

        var petSameIdDifferentInstance = visit.pet().toBuilder().name("Simon").build();
        var update = visit.toBuilder().pet(petSameIdDifferentInstance).build();

        var sql = captureFirstUpdateSql(() -> repo.updateAndFetch(update));
        assertNull(sql);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private Sql captureFirstUpdateSql(ThrowingSupplier<?> action) {
        AtomicReference<Sql> ref = new AtomicReference<>();
        observe(s -> {
            if (s.statement().startsWith("UPDATE")) {
                ref.compareAndSet(null, s);
            }
        }, () -> {
            try {
                return action.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return ref.getPlain();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
