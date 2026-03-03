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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.core.spi.RefFactory;

public class StormModuleTest {

    // Test entities and projections.

    public record SimpleEntity(@PK Integer id, @Nonnull String name) implements Entity<Integer> {}

    public record StringIdEntity(@PK String id, @Nonnull String name) implements Entity<String> {}

    public record LongIdEntity(@PK Long id, @Nonnull String name) implements Entity<Long> {}

    public record SimpleProjection(@PK Integer id, @Nonnull String label) implements Projection<Integer> {}

    // A Data record without @PK annotation - triggers null pkType fallback paths.
    public record NoPkData(String value) implements Data {}

    // Wrapper records with Ref fields.

    public record EntityRefHolder(@Nonnull Ref<SimpleEntity> entity) {}

    public record StringIdRefHolder(@Nonnull Ref<StringIdEntity> entity) {}

    public record LongIdRefHolder(@Nonnull Ref<LongIdEntity> entity) {}

    public record ProjectionRefHolder(@Nullable Ref<SimpleProjection> projection) {}

    public record NullableRefHolder(@Nullable Ref<SimpleEntity> entity) {}

    public record RefListHolder(@Nonnull List<Ref<SimpleEntity>> entities) {}

    public record RefSetHolder(@Nonnull Set<Ref<SimpleEntity>> entities) {}

    public record RefMapHolder(@Nonnull Map<String, Ref<SimpleEntity>> entities) {}

    public record NoPkRefHolder(@Nullable Ref<NoPkData> data) {}

    public record NoPkRefListHolder(@Nonnull List<Ref<NoPkData>> data) {}

    // Tests for constructor variations.

    @Test
    public void constructorWithNullRefFactoryShouldCreateModuleWithDetachedRefs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule((RefFactory) null));

        String json = "{\"entity\":1}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
    }

    @Test
    public void constructorWithRefFactoryShouldUseFactoryForDeserialization() throws Exception {
        RefFactory factory = new RefFactory() {
            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
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
        mapper.registerModule(new StormModule(factory));

        String json = "{\"entity\":42}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
    }

    @Test
    public void constructorWithSupplierShouldUseSuppliedFactory() throws Exception {
        RefFactory factory = new RefFactory() {
            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
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

        String json = "{\"entity\":7}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(7, holder.entity().id());
    }

    @Test
    public void constructorWithNullSupplierResultShouldCreateDetachedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule(() -> null));

        String json = "{\"entity\":7}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(7, holder.entity().id());
    }

    // Tests for Ref serialization.

    @Test
    public void unloadedRefShouldSerializeAsRawId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<SimpleEntity> ref = Ref.of(SimpleEntity.class, 42);
        EntityRefHolder holder = new EntityRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":42}", json);
    }

    @Test
    public void loadedEntityRefShouldSerializeWithEntityWrapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        SimpleEntity entity = new SimpleEntity(1, "Alice");
        Ref<SimpleEntity> ref = Ref.of(entity);
        EntityRefHolder holder = new EntityRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":{\"@entity\":{\"id\":1,\"name\":\"Alice\"}}}", json);
    }

    @Test
    public void loadedProjectionRefShouldSerializeWithProjectionWrapperAndId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        SimpleProjection projection = new SimpleProjection(5, "Test");
        Ref<SimpleProjection> ref = Ref.of(projection, 5);
        ProjectionRefHolder holder = new ProjectionRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"projection\":{\"@id\":5,\"@projection\":{\"id\":5,\"label\":\"Test\"}}}", json);
    }

    @Test
    public void unloadedRefWithStringIdShouldSerializeAsStringValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<StringIdEntity> ref = Ref.of(StringIdEntity.class, "abc-123");
        StringIdRefHolder holder = new StringIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":\"abc-123\"}", json);
    }

    @Test
    public void unloadedRefWithLongIdShouldSerializeAsLongValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<LongIdEntity> ref = Ref.of(LongIdEntity.class, 9999999999L);
        LongIdRefHolder holder = new LongIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":9999999999}", json);
    }

    // Tests for Ref deserialization.

    @Test
    public void rawIntegerIdShouldDeserializeToUnloadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":42}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
        assertNull(holder.entity().getOrNull());
    }

    @Test
    public void rawStringIdShouldDeserializeToUnloadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":\"abc-123\"}";
        StringIdRefHolder holder = mapper.readValue(json, StringIdRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals("abc-123", holder.entity().id());
    }

    @Test
    public void rawLongIdShouldDeserializeToUnloadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":9999999999}";
        LongIdRefHolder holder = mapper.readValue(json, LongIdRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(9999999999L, holder.entity().id());
    }

    @Test
    public void entityObjectShouldDeserializeToLoadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":{\"@entity\":{\"id\":1,\"name\":\"Alice\"}}}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
        SimpleEntity loadedEntity = holder.entity().getOrNull();
        assertNotNull(loadedEntity);
        assertEquals("Alice", loadedEntity.name());
    }

    @Test
    public void projectionObjectShouldDeserializeToLoadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"projection\":{\"@id\":5,\"@projection\":{\"id\":5,\"label\":\"Test\"}}}";
        ProjectionRefHolder holder = mapper.readValue(json, ProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertEquals(5, holder.projection().id());
        SimpleProjection loadedProjection = holder.projection().getOrNull();
        assertNotNull(loadedProjection);
        assertEquals("Test", loadedProjection.label());
    }

    @Test
    public void nullRefShouldDeserializeToNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":null}";
        NullableRefHolder holder = mapper.readValue(json, NullableRefHolder.class);
        assertNull(holder.entity());
    }

    @Test
    public void refListWithNullElementsShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entities\":[null,1,2]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, holder.entities().size());
        assertNull(holder.entities().get(0));
        assertEquals(1, holder.entities().get(1).id());
        assertEquals(2, holder.entities().get(2).id());
    }

    @Test
    public void refObjectWithoutEntityOrProjectionFieldShouldThrowException() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entity\":{\"unknown\":\"field\"}}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, EntityRefHolder.class));
    }

    @Test
    public void projectionObjectWithoutIdFieldShouldThrowException() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"projection\":{\"@projection\":{\"id\":5,\"label\":\"Test\"}}}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, ProjectionRefHolder.class));
    }

    @Test
    public void entityRefSerializationShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Unloaded ref round-trip.
        Ref<SimpleEntity> unloadedRef = Ref.of(SimpleEntity.class, 42);
        EntityRefHolder unloadedHolder = new EntityRefHolder(unloadedRef);
        String unloadedJson = mapper.writeValueAsString(unloadedHolder);
        EntityRefHolder deserializedUnloaded = mapper.readValue(unloadedJson, EntityRefHolder.class);
        assertEquals(unloadedHolder, deserializedUnloaded);

        // Loaded ref round-trip.
        SimpleEntity entity = new SimpleEntity(1, "Alice");
        Ref<SimpleEntity> loadedRef = Ref.of(entity);
        EntityRefHolder loadedHolder = new EntityRefHolder(loadedRef);
        String loadedJson = mapper.writeValueAsString(loadedHolder);
        EntityRefHolder deserializedLoaded = mapper.readValue(loadedJson, EntityRefHolder.class);
        assertEquals(loadedHolder, deserializedLoaded);
    }

    @Test
    public void projectionRefSerializationShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Unloaded projection ref round-trip.
        Ref<SimpleProjection> unloadedRef = Ref.of(SimpleProjection.class, 5);
        ProjectionRefHolder unloadedHolder = new ProjectionRefHolder(unloadedRef);
        String unloadedJson = mapper.writeValueAsString(unloadedHolder);
        ProjectionRefHolder deserializedUnloaded = mapper.readValue(unloadedJson, ProjectionRefHolder.class);
        assertEquals(unloadedHolder, deserializedUnloaded);

        // Loaded projection ref round-trip.
        SimpleProjection projection = new SimpleProjection(5, "Test");
        Ref<SimpleProjection> loadedRef = Ref.of(projection, 5);
        ProjectionRefHolder loadedHolder = new ProjectionRefHolder(loadedRef);
        String loadedJson = mapper.writeValueAsString(loadedHolder);
        ProjectionRefHolder deserializedLoaded = mapper.readValue(loadedJson, ProjectionRefHolder.class);
        assertEquals(loadedHolder, deserializedLoaded);
    }

    @Test
    public void stringIdRefShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<StringIdEntity> ref = Ref.of(StringIdEntity.class, "my-id");
        StringIdRefHolder holder = new StringIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        StringIdRefHolder deserialized = mapper.readValue(json, StringIdRefHolder.class);
        assertEquals(holder, deserialized);
    }

    @Test
    public void loadedStringIdEntityRefShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        StringIdEntity entity = new StringIdEntity("abc", "Test");
        Ref<StringIdEntity> ref = Ref.of(entity);
        StringIdRefHolder holder = new StringIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        StringIdRefHolder deserialized = mapper.readValue(json, StringIdRefHolder.class);
        assertEquals(holder, deserialized);
    }

    @Test
    public void loadedLongIdEntityRefShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        LongIdEntity entity = new LongIdEntity(999L, "Test");
        Ref<LongIdEntity> ref = Ref.of(entity);
        LongIdRefHolder holder = new LongIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        LongIdRefHolder deserialized = mapper.readValue(json, LongIdRefHolder.class);
        assertEquals(holder, deserialized);
    }

    // Tests for fallback deserialization paths (when pkType is null).

    @Test
    public void noPkDataWithSmallIntegerIdShouldDeserializeViaFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // NoPkData has no @PK field, so pkType will be null, triggering the fallback integer path.
        String json = "{\"data\":42}";
        NoPkRefHolder holder = mapper.readValue(json, NoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    @Test
    public void noPkDataWithLargeIntegerIdShouldDeserializeAsLongViaFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Large number exceeding int range triggers the long branch in fallback.
        String json = "{\"data\":9999999999}";
        NoPkRefHolder holder = mapper.readValue(json, NoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(9999999999L, holder.data().id());
    }

    @Test
    public void noPkDataWithFloatIdShouldDeserializeAsDoubleViaFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Float value triggers VALUE_NUMBER_FLOAT branch in fallback.
        String json = "{\"data\":3.14}";
        NoPkRefHolder holder = mapper.readValue(json, NoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(3.14, holder.data().id());
    }

    @Test
    public void noPkDataWithStringIdShouldDeserializeAsStringViaFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // String value triggers VALUE_STRING branch in fallback.
        String json = "{\"data\":\"my-id\"}";
        NoPkRefHolder holder = mapper.readValue(json, NoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals("my-id", holder.data().id());
    }

    // Tests for deserializeIdFromNode fallback paths (projection deserialization).

    public record NoPkProjection(String label) implements Projection<Void> {}

    public record NoPkProjectionRefHolder(@Nullable Ref<NoPkProjection> projection) {}

    @Test
    public void noPkProjectionWithIntIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Projection path with @id node and no @PK on target - exercises deserializeIdFromNode int fallback.
        String json = "{\"projection\":{\"@id\":42,\"@projection\":{\"label\":\"Test\"}}}";
        NoPkProjectionRefHolder holder = mapper.readValue(json, NoPkProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertEquals(42, holder.projection().id());
    }

    @Test
    public void noPkProjectionWithLongIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Long value in node triggers isLong fallback.
        String json = "{\"projection\":{\"@id\":9999999999,\"@projection\":{\"label\":\"Test\"}}}";
        NoPkProjectionRefHolder holder = mapper.readValue(json, NoPkProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertEquals(9999999999L, holder.projection().id());
    }

    @Test
    public void noPkProjectionWithDoubleIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Double value in node triggers isDouble fallback.
        String json = "{\"projection\":{\"@id\":3.14,\"@projection\":{\"label\":\"Test\"}}}";
        NoPkProjectionRefHolder holder = mapper.readValue(json, NoPkProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertEquals(3.14, holder.projection().id());
    }

    @Test
    public void noPkProjectionWithStringIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // String value in node triggers isTextual fallback.
        String json = "{\"projection\":{\"@id\":\"abc\",\"@projection\":{\"label\":\"Test\"}}}";
        NoPkProjectionRefHolder holder = mapper.readValue(json, NoPkProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertEquals("abc", holder.projection().id());
    }

    @Test
    public void noPkProjectionWithObjectIdNodeShouldDeserializeViaObjectFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Object value in node triggers the final Object fallback.
        String json = "{\"projection\":{\"@id\":{\"key\":\"val\"},\"@projection\":{\"label\":\"Test\"}}}";
        NoPkProjectionRefHolder holder = mapper.readValue(json, NoPkProjectionRefHolder.class);
        assertNotNull(holder.projection());
        assertNotNull(holder.projection().id());
    }

    // Tests for Ref in collections (Set, Map).

    @Test
    public void refInSetShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entities\":[1,2,3]}";
        RefSetHolder holder = mapper.readValue(json, RefSetHolder.class);
        assertEquals(3, holder.entities().size());
    }

    @Test
    public void refInMapValueShouldDeserializeCorrectly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"entities\":{\"a\":1,\"b\":2}}";
        RefMapHolder holder = mapper.readValue(json, RefMapHolder.class);
        assertEquals(2, holder.entities().size());
        assertEquals(1, holder.entities().get("a").id());
        assertEquals(2, holder.entities().get("b").id());
    }

    // Tests for error paths.

    @Test
    public void booleanTokenShouldThrowExceptionDuringRefDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Boolean token is not a valid Ref value, should trigger the default case in deserialize.
        String json = "{\"entity\":true}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, EntityRefHolder.class));
    }

    @Test
    public void arrayTokenShouldThrowExceptionDuringRefDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Array token is not a valid Ref value.
        String json = "{\"entity\":[1,2,3]}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, EntityRefHolder.class));
    }

    // Tests for list deserialization with NoPk data (fallback path in list context).

    @Test
    public void noPkRefListWithMixedIdTypesShouldDeserializeViaFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"data\":[1,\"two\",3]}";
        NoPkRefListHolder holder = mapper.readValue(json, NoPkRefListHolder.class);
        assertEquals(3, holder.data().size());
        assertEquals(1, ((Number) holder.data().get(0).id()).intValue());
        assertEquals("two", holder.data().get(1).id());
        assertEquals(3, ((Number) holder.data().get(2).id()).intValue());
    }

    // Test for serialization of loaded Data ref (not Entity, not Projection)
    // when Data type is used as loaded ref via @entity path.

    @Test
    public void unloadedNoPkRefShouldSerializeAsRawId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<NoPkData> ref = Ref.of(NoPkData.class, 42);
        NoPkRefHolder holder = new NoPkRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        // With no pkType, falls through to defaultSerializeValue.
        assertTrue(json.contains("42"));
    }

    // Tests for the resolveRefTargetType edge cases.

    @Test
    public void refSetSerializationShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Set<Ref<SimpleEntity>> refs = Set.of(
                Ref.of(SimpleEntity.class, 1),
                Ref.of(SimpleEntity.class, 2));
        RefSetHolder holder = new RefSetHolder(refs);
        String json = mapper.writeValueAsString(holder);
        assertNotNull(json);
        // Verify it deserializes back.
        RefSetHolder deserialized = mapper.readValue(json, RefSetHolder.class);
        assertEquals(2, deserialized.entities().size());
    }

    @Test
    public void refMapSerializationShouldWork() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Map<String, Ref<SimpleEntity>> refs = Map.of(
                "a", Ref.of(SimpleEntity.class, 1),
                "b", Ref.of(SimpleEntity.class, 2));
        RefMapHolder holder = new RefMapHolder(refs);
        String json = mapper.writeValueAsString(holder);
        assertNotNull(json);
    }

    // Tests for createLoadedRefWithId - Entity via @projection path.

    @Test
    public void projectionPathWithEntityDataShouldCreateEntityRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // When @projection contains an Entity, createLoadedRefWithId should take the Entity branch.
        // Use EntityRefHolder but with @projection format instead of @entity format.
        String json = "{\"entity\":{\"@id\":1,\"@projection\":{\"id\":1,\"name\":\"Alice\"}}}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
        // Since SimpleEntity is an Entity, it should take the Entity branch in createLoadedRefWithId.
        assertNotNull(holder.entity().getOrNull());
    }

    // Tests for createLoadedRefWithId - Data that is not Entity or Projection, with id.

    public record DataRefHolder(@Nullable Ref<NoPkData> data) {}

    @Test
    public void projectionPathWithPlainDataAndIdShouldCreateUnloadedRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // NoPkData is Data but not Entity or Projection.
        // @projection with @id should take the fallback "if (id != null) return Ref.of(targetClass, id)" path.
        String json = "{\"data\":{\"@id\":42,\"@projection\":{\"value\":\"test\"}}}";
        DataRefHolder holder = mapper.readValue(json, DataRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    // Test for createLoadedRef with non-Entity in @entity path.

    @Test
    public void entityPathWithNonEntityDataShouldThrowException() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // NoPkData is Data but not Entity. Using @entity with non-Entity data should throw.
        String json = "{\"data\":{\"@entity\":{\"value\":\"test\"}}}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, DataRefHolder.class));
    }

    // Test for resolvePkType returning NO_PK.

    @Test
    public void noPkDataSerializerShouldHandleMissingPkTypeGracefully() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // NoPkData has no @PK, so serializeId falls through to defaultSerializeValue.
        Ref<NoPkData> ref = Ref.of(NoPkData.class, "text-id");
        DataRefHolder holder = new DataRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("text-id"));
    }

    // Test for null nullable projection ref in holder.

    @Test
    public void nullProjectionRefShouldSerializeToNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        ProjectionRefHolder holder = new ProjectionRefHolder(null);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"projection\":null}", json);
    }

    @Test
    public void nullProjectionRefShouldDeserializeFromNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        String json = "{\"projection\":null}";
        ProjectionRefHolder holder = mapper.readValue(json, ProjectionRefHolder.class);
        assertNull(holder.projection());
    }

    // Test for deserializeId default token case.

    @Test
    public void noPkDataWithBooleanShouldThrowExceptionViaFallbackPath() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Boolean values trigger the default case in the main deserialize switch (not NUMBER_INT/FLOAT/STRING).
        String json = "{\"data\":true}";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, NoPkRefHolder.class));
    }

    // Test for createRefFromId with null id.

    @Test
    public void noPkRefWithNullIdInListShouldReturnNullRef() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Test null handling within list deserialization for NoPkData refs.
        String json = "{\"data\":[null, 1]}";
        NoPkRefListHolder holder = mapper.readValue(json, NoPkRefListHolder.class);
        assertEquals(2, holder.data().size());
        assertNull(holder.data().get(0));
        assertNotNull(holder.data().get(1));
    }

    // Test for Ref field with Double PK type.

    public record DoubleIdEntity(@PK Double id, @Nonnull String name) implements Entity<Double> {}

    public record DoubleIdRefHolder(@Nonnull Ref<DoubleIdEntity> entity) {}

    @Test
    public void doubleIdRefShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        Ref<DoubleIdEntity> ref = Ref.of(DoubleIdEntity.class, 3.14);
        DoubleIdRefHolder holder = new DoubleIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":3.14}", json);
        DoubleIdRefHolder deserialized = mapper.readValue(json, DoubleIdRefHolder.class);
        assertEquals(3.14, deserialized.entity().id());
    }

    @Test
    public void loadedDoubleIdEntityRefShouldRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        DoubleIdEntity entity = new DoubleIdEntity(2.71, "Euler");
        Ref<DoubleIdEntity> ref = Ref.of(entity);
        DoubleIdRefHolder holder = new DoubleIdRefHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("@entity"));
        DoubleIdRefHolder deserialized = mapper.readValue(json, DoubleIdRefHolder.class);
        assertEquals(2.71, deserialized.entity().id());
    }

    // Test for deserializing raw Ref without type information should throw.

    @SuppressWarnings("rawtypes")
    @Test
    public void rawRefWithoutTypeInfoShouldThrowOnDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new StormModule());

        // Deserializing a raw Ref (no generic type info) should throw because targetType is null.
        String json = "42";
        assertThrows(JsonMappingException.class, () ->
                mapper.readValue(json, Ref.class));
    }

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
