package st.orm.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.core.spi.RefFactory;

/**
 * Additional tests for {@link StormModule} targeting remaining uncovered edge cases:
 * <ul>
 *   <li>Lines 122/126: resolveRefTargetType with null type and container with null content type</li>
 *   <li>Line 129: resolveRefTargetType returning null for non-Ref type</li>
 *   <li>Lines 176/179: createContextual with null property in RefSerializer</li>
 *   <li>Lines 267/271: deserialize with null/VALUE_NULL current token</li>
 *   <li>Lines 343-344: deserializeId default case</li>
 *   <li>Lines 352: createLoadedRef with null entity</li>
 *   <li>Lines 364: createLoadedRefWithId with null data</li>
 *   <li>Line 376: @projection with null @id</li>
 *   <li>Line 381: createRefFromId with null id</li>
 * </ul>
 */
public class StormModuleEdgeCaseTest {

    public record SimpleEntity(@PK Integer id, @Nonnull String name) implements Entity<Integer> {}

    public record SimpleProjection(@PK Integer id, @Nonnull String label) implements Projection<Integer> {}

    public record NoPkData(String value) implements Data {}

    public record EntityRefHolder(@Nonnull Ref<SimpleEntity> entity) {}

    public record NullableEntityRefHolder(@Nullable Ref<SimpleEntity> entity) {}

    public record ProjectionRefHolder(@Nullable Ref<SimpleProjection> projection) {}

    public record NoPkRefHolder(@Nullable Ref<NoPkData> data) {}

    public record RefListHolder(@Nonnull List<Ref<SimpleEntity>> entities) {}

    // -- Tests for RefSerializer createContextual with null property --

    @Test
    public void serializerShouldHandleDirectRefSerializationWithoutProperty() throws Exception {
        // Serializing a raw Ref directly (not as a field of another object) means
        // createContextual may be called with null property.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<SimpleEntity> ref = Ref.of(SimpleEntity.class, 42);
        String json = mapper.writeValueAsString(ref);
        // Without property context, the serializer uses default serialize path.
        assertNotNull(json);
        assertTrue(json.contains("42"));
    }

    // -- Tests for resolveRefTargetType returning null for non-Ref type --

    @Test
    public void nonRefFieldShouldNotAffectSerialization() throws Exception {
        // This record has a non-Ref field. The StormModule should not interfere with it.
        record NonRefHolder(@Nonnull String value) {}

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        NonRefHolder holder = new NonRefHolder("test");
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"value\":\"test\"}", json);
    }

    // -- Tests for deserialize edge cases --

    @Test
    public void refListWithLoadedEntitiesAndNullsShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // List with mixed: null, raw id, and loaded entity.
        String json = "{\"entities\":[null,1,{\"@entity\":{\"id\":2,\"name\":\"Bob\"}}]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, holder.entities().size());
        assertNull(holder.entities().get(0));
        assertEquals(1, holder.entities().get(1).id());
        assertEquals(2, holder.entities().get(2).id());
        assertNotNull(holder.entities().get(2).getOrNull());
    }

    // -- Tests for refFactory supplier edge cases --

    @Test
    public void supplierReturningNullFactoryShouldCreateDetachedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule(() -> null));

        String json = "{\"entity\":42}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
        // Should be a detached ref (no loaded entity).
        assertNull(holder.entity().getOrNull());
    }

    // -- Test for default constructor (no-arg) --

    @Test
    public void defaultConstructorShouldCreateModuleWithDetachedRefs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Deserialize with no RefFactory - should create detached ref.
        String json = "{\"entity\":99}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(99, holder.entity().id());
        assertNull(holder.entity().getOrNull());
    }

    // -- Tests for projectionPathWithNullId scenario --
    // The createLoadedRefWithId with null data can occur if deserializeObject
    // passes null data, but in practice Jackson would throw on null tree value first.
    // We test the closest reachable path.

    @Test
    public void projectionWithEntityTypeInProjectionPathShouldCreateEntityRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // @projection containing a SimpleEntity (which is Entity type).
        // createLoadedRefWithId should take the Entity branch (line 366-367).
        String json = "{\"entity\":{\"@id\":1,\"@projection\":{\"id\":1,\"name\":\"Via Projection Path\"}}}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
        SimpleEntity loaded = holder.entity().getOrNull();
        assertNotNull(loaded);
        assertEquals("Via Projection Path", loaded.name());
    }

    // -- Test for refFactory with actual factory implementation --

    @Test
    public void refFactorySupplierShouldBeCalledDuringDeserialization() throws Exception {
        var factoryCallCount = new int[]{0};
        RefFactory factory = new RefFactory() {
            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
                factoryCallCount[0]++;
                return Ref.of(type, pk);
            }

            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk) {
                return Ref.of(type(record), pk);
            }

            @SuppressWarnings("unchecked")
            private <T extends Data> Class<T> type(T record) {
                return (Class<T>) record.getClass();
            }
        };
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule(() -> factory));

        String json = "{\"entities\":[1,2,3]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, holder.entities().size());
        assertEquals(3, factoryCallCount[0]);
    }

    // -- Test for NoPkData in Map values with projection format --

    @Test
    public void noPkDataWithProjectionFormatAndNullIdShouldThrow() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // @projection with @id:null for NoPkData type.
        // This should trigger the "cannot be null" path or a different error.
        String json = "{\"data\":{\"@id\":null,\"@projection\":{\"value\":\"test\"}}}";
        // With a null @id, Jackson may still parse it as null, then createLoadedRefWithId
        // checks if data is null (no), then Entity (no), then Projection (no),
        // then id != null (false since null), then throw (line 376).
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, NoPkRefHolder.class));
    }

    // -- Test for NoPkData loaded projection ref with non-null id --

    public record NoPkDataProjectionHolder(@Nullable Ref<NoPkData> data) {}

    @Test
    public void noPkDataWithProjectionFormatAndNonNullIdShouldCreateRefWithId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // NoPkData is Data but not Entity or Projection.
        // @projection with @id:42 should hit the fallback "Ref.of(targetClass, id)" path (line 373).
        String json = "{\"data\":{\"@id\":42,\"@projection\":{\"value\":\"test\"}}}";
        NoPkDataProjectionHolder holder = mapper.readValue(json, NoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    // -- Test for empty Ref list --

    @Test
    public void emptyRefListShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entities\":[]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertNotNull(holder.entities());
        assertEquals(0, holder.entities().size());
    }

    // -- Test for loaded entity ref in list --

    @Test
    public void loadedEntityRefInListShouldSerializeAndDeserialize() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        List<Ref<SimpleEntity>> refs = List.of(
                Ref.of(new SimpleEntity(1, "Alice")),
                Ref.of(SimpleEntity.class, 2),
                Ref.of(new SimpleEntity(3, "Charlie")));
        RefListHolder holder = new RefListHolder(refs);
        String json = mapper.writeValueAsString(holder);
        assertNotNull(json);

        RefListHolder deserialized = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, deserialized.entities().size());
        assertNotNull(deserialized.entities().get(0).getOrNull());
        assertNull(deserialized.entities().get(1).getOrNull());
        assertNotNull(deserialized.entities().get(2).getOrNull());
    }

    // -- Test for projection ref with known PK in list context --

    public record ProjectionRefListHolder(@Nonnull List<Ref<SimpleProjection>> projections) {}

    @Test
    public void loadedProjectionRefInListShouldSerializeAndDeserialize() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        List<Ref<SimpleProjection>> refs = List.of(
                Ref.of(new SimpleProjection(1, "First"), 1),
                Ref.of(SimpleProjection.class, 2));
        ProjectionRefListHolder holder = new ProjectionRefListHolder(refs);
        String json = mapper.writeValueAsString(holder);
        assertNotNull(json);

        ProjectionRefListHolder deserialized = mapper.readValue(json, ProjectionRefListHolder.class);
        assertEquals(2, deserialized.projections().size());
        assertNotNull(deserialized.projections().get(0).getOrNull());
        assertNull(deserialized.projections().get(1).getOrNull());
    }

    // -- Test for multiple entity types in same ObjectMapper --

    public record MultiTypeHolder(
            @Nullable Ref<SimpleEntity> entity,
            @Nullable Ref<SimpleProjection> projection) {}

    @Test
    public void multipleRefTypesInSameHolderShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":42,\"projection\":7}";
        MultiTypeHolder holder = mapper.readValue(json, MultiTypeHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
        assertNotNull(holder.projection());
        assertEquals(7, holder.projection().id());
    }
}
