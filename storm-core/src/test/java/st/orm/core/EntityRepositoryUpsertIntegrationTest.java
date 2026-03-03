package st.orm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import st.orm.EntityCallback;
import st.orm.PersistenceException;
import st.orm.core.model.City;
import st.orm.core.template.ORMTemplate;

/**
 * Integration tests for EntityRepositoryImpl focusing on entity lookup by Ref,
 * batch operations, callback handling, and edge case operations.
 * Note: Upsert tests are excluded because the default H2 implementation does not support upsert.
 */
@SuppressWarnings("ALL")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = IntegrationConfig.class)
@DataJpaTest(showSql = false)
public class EntityRepositoryUpsertIntegrationTest {

    @Autowired
    private DataSource dataSource;

    // Upsert: not available on H2, verify exception

    @Test
    public void testUpsertNotAvailableOnH2() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        assertThrows(PersistenceException.class,
                () -> cities.upsert(City.builder().name("UpsertNew").build()));
    }

    @Test
    public void testUpsertAndFetchIdNotAvailableOnH2() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        assertThrows(PersistenceException.class,
                () -> cities.upsertAndFetchId(City.builder().name("UpsertNew").build()));
    }

    @Test
    public void testBatchUpsertNotAvailableOnH2() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        assertThrows(PersistenceException.class,
                () -> cities.upsert(List.of(City.builder().name("A").build())));
    }

    @Test
    public void testBatchUpsertAndFetchIdsNotAvailableOnH2() {
        var orm = ORMTemplate.of(dataSource);
        var cities = orm.entity(City.class);
        assertThrows(PersistenceException.class,
                () -> cities.upsertAndFetchIds(List.of(City.builder().name("A").build())));
    }

    // withEntityCallbacks: adding callbacks to existing ORM template

    @Test
    public void testWithEntityCallbacksAddsCallbacks() {
        var orm = ORMTemplate.of(dataSource);
        assertTrue(orm.entityCallbacks().isEmpty());
        var ormWithCallbacks = orm.withEntityCallback(new EntityCallback<City>() {});
        assertEquals(1, ormWithCallbacks.entityCallbacks().size());
        // Original should be unchanged.
        assertTrue(orm.entityCallbacks().isEmpty());
    }

    // Multiple entity types with separate callbacks

    @Test
    public void testMultipleCallbacksForSameType() {
        List<String> log1 = new ArrayList<>();
        List<String> log2 = new ArrayList<>();
        var orm = ORMTemplate.of(dataSource)
                .withEntityCallback(new EntityCallback<City>() {
                    @Override
                    public City beforeInsert(@Nonnull City entity) {
                        log1.add("cb1:" + entity.name());
                        return entity;
                    }
                })
                .withEntityCallback(new EntityCallback<City>() {
                    @Override
                    public City beforeInsert(@Nonnull City entity) {
                        log2.add("cb2:" + entity.name());
                        return entity;
                    }
                });
        orm.entity(City.class).insert(City.builder().name("multi").build());
        assertEquals(1, log1.size());
        assertEquals(1, log2.size());
    }
}
