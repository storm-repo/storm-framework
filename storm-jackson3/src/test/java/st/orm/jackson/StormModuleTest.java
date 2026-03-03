package st.orm.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PK;
import st.orm.Projection;
import st.orm.Ref;
import st.orm.core.spi.RefFactory;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

class StormModuleTest {

    public record SimpleEntity(@PK Integer id, @Nonnull String name)
            implements Entity<Integer> {}

    public record SimpleProjection(@PK Integer id, @Nonnull String label)
            implements Projection<Integer> {}

    public record EntityHolder(@Nullable Ref<SimpleEntity> entity) {}

    public record ProjectionHolder(@Nullable Ref<SimpleProjection> projection) {}

    public record EntityListHolder(@Nullable List<Ref<SimpleEntity>> entities) {}

    // RefSerializer tests

    @org.junit.jupiter.api.Test
    void serializeUnloadedEntityRefToRawId() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new EntityHolder(Ref.of(SimpleEntity.class, 42));
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":42}", json);
    }

    @org.junit.jupiter.api.Test
    void serializeLoadedEntityRefWithEntityWrapper() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var entity = new SimpleEntity(7, "Test");
        var holder = new EntityHolder(Ref.of(entity));
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":{\"@entity\":{\"id\":7,\"name\":\"Test\"}}}", json);
    }

    @org.junit.jupiter.api.Test
    void serializeLoadedProjectionRefWithProjectionWrapper() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var projection = new SimpleProjection(3, "Label");
        var holder = new ProjectionHolder(Ref.of(projection, 3));
        var json = mapper.writeValueAsString(holder);
        assertEquals(
                "{\"projection\":{\"@id\":3,\"@projection\":{\"id\":3,\"label\":\"Label\"}}}",
                json);
    }

    @org.junit.jupiter.api.Test
    void serializeNullRefFieldToNull() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new EntityHolder(null);
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":null}", json);
    }

    // RefDeserializer tests

    @org.junit.jupiter.api.Test
    void deserializeRawIdToDetachedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entity\":42}", EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeRawIdWithRefFactory() throws Exception {
        RefFactory factory = new RefFactory() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends st.orm.Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID id) {
                return (Ref<T>) Ref.of((Class) type, id);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends st.orm.Data, ID> Ref<T> create(@Nonnull T record, @Nonnull ID id) {
                return (Ref<T>) Ref.of((Class) record.getClass(), id);
            }
        };
        var mapper = JsonMapper.builder()
                .addModule(new StormModule(factory))
                .build();
        var holder = mapper.readValue("{\"entity\":99}", EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(99, holder.entity().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeEntityObjectToLoadedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"entity\":{\"@entity\":{\"id\":7,\"name\":\"Test\"}}}";
        var holder = mapper.readValue(json, EntityHolder.class);
        assertNotNull(holder.entity());
        assertTrue(holder.entity().isLoaded());
        var entity = holder.entity().fetch();
        assertInstanceOf(SimpleEntity.class, entity);
        assertEquals(7, entity.id());
        assertEquals("Test", entity.name());
    }

    @org.junit.jupiter.api.Test
    void deserializeProjectionObjectToLoadedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"projection\":{\"@id\":3,\"@projection\":{\"id\":3,\"label\":\"Label\"}}}";
        var holder = mapper.readValue(json, ProjectionHolder.class);
        assertNotNull(holder.projection());
        assertTrue(holder.projection().isLoaded());
        var projection = holder.projection().fetch();
        assertInstanceOf(SimpleProjection.class, projection);
        assertEquals(3, projection.id());
        assertEquals("Label", projection.label());
    }

    @org.junit.jupiter.api.Test
    void deserializeNullRefFieldToNull() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entity\":null}", EntityHolder.class);
        assertNull(holder.entity());
    }

    @org.junit.jupiter.api.Test
    void deserializeObjectWithoutEntityOrProjectionFieldShouldThrow() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"entity\":{\"unknown\":1}}", EntityHolder.class));
    }

    @org.junit.jupiter.api.Test
    void deserializeStringIdToDetachedRef() throws Exception {
        // Test VALUE_STRING token path
        record StringIdEntity(@PK String id, @Nonnull String name)
                implements Entity<String> {}
        record StringIdHolder(@Nullable Ref<StringIdEntity> entity) {}
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entity\":\"abc\"}", StringIdHolder.class);
        assertNotNull(holder.entity());
        assertEquals("abc", holder.entity().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeListOfRefsWithNullElement() throws Exception {
        // Test List<Ref<T>> with null elements - exercises the container unwrapping + null handling
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"entities\":[42, null, 7]}";
        var holder = mapper.readValue(json, EntityListHolder.class);
        assertNotNull(holder.entities());
        assertEquals(3, holder.entities().size());
        assertEquals(42, holder.entities().get(0).id());
        assertNull(holder.entities().get(1));
        assertEquals(7, holder.entities().get(2).id());
    }

    @org.junit.jupiter.api.Test
    void roundTripEntityRefShouldPreserveLoadedState() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var entity = new SimpleEntity(5, "RoundTrip");
        var original = new EntityHolder(Ref.of(entity));
        var json = mapper.writeValueAsString(original);
        var restored = mapper.readValue(json, EntityHolder.class);
        assertNotNull(restored.entity());
        assertTrue(restored.entity().isLoaded());
        assertEquals(entity, restored.entity().fetch());
    }

    @org.junit.jupiter.api.Test
    void roundTripProjectionRefShouldPreserveLoadedState() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var projection = new SimpleProjection(10, "Proj");
        var original = new ProjectionHolder(Ref.of(projection, 10));
        var json = mapper.writeValueAsString(original);
        var restored = mapper.readValue(json, ProjectionHolder.class);
        assertNotNull(restored.projection());
        assertTrue(restored.projection().isLoaded());
        assertEquals(projection, restored.projection().fetch());
    }

    // Tests for Data record without @PK (fallback deserialization paths)

    // Data record without @PK annotation, so resolvePkType returns null and fallback paths execute.
    public record NoPkData(Integer id, @Nonnull String value) implements Data {}

    public record NoPkDataHolder(@Nullable Ref<NoPkData> data) {}

    public record NoPkDataProjection(Integer id, @Nonnull String value)
            implements Projection<Integer> {}

    public record NoPkDataProjectionHolder(@Nullable Ref<NoPkDataProjection> data) {}

    @org.junit.jupiter.api.Test
    void deserializeIntIdFallbackForNoPkData() throws Exception {
        // When pkType is null (no @PK on target), the fallback deserializeId path is used.
        // This exercises the VALUE_NUMBER_INT fallback in deserializeId.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"data\":42}", NoPkDataHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    @org.junit.jupiter.api.Test
    void deserializeStringIdFallbackForNoPkData() throws Exception {
        // Exercises the VALUE_STRING fallback in deserializeId.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"data\":\"abc\"}", NoPkDataHolder.class);
        assertNotNull(holder.data());
        assertEquals("abc", holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeFloatIdFallbackForNoPkData() throws Exception {
        // Exercises the VALUE_NUMBER_FLOAT fallback in deserializeId.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"data\":3.14}", NoPkDataHolder.class);
        assertNotNull(holder.data());
        assertEquals(3.14, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeLargeIntIdFallbackForNoPkData() throws Exception {
        // Exercises the VALUE_NUMBER_INT fallback with a value > Integer.MAX_VALUE.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        long largeValue = (long) Integer.MAX_VALUE + 1L;
        var holder = mapper.readValue("{\"data\":" + largeValue + "}", NoPkDataHolder.class);
        assertNotNull(holder.data());
        assertEquals(largeValue, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeNegativeLargeIntIdFallbackForNoPkData() throws Exception {
        // Exercises the VALUE_NUMBER_INT fallback with a value < Integer.MIN_VALUE.
        // This covers the short-circuit branch when value < Integer.MIN_VALUE.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        long negativeValue = (long) Integer.MIN_VALUE - 1L;
        var holder = mapper.readValue("{\"data\":" + negativeValue + "}", NoPkDataHolder.class);
        assertNotNull(holder.data());
        assertEquals(negativeValue, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeProjectionObjectWithIntIdNodeFallbackForNoPk() throws Exception {
        // Exercises the deserializeIdFromNode fallback path for a Projection without @PK.
        // When pkType is null, the node.isInt() path is taken.
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"data\":{\"@id\":5,\"@projection\":{\"id\":5,\"value\":\"hello\"}}}";
        var holder = mapper.readValue(json, NoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertTrue(holder.data().isLoaded());
        assertEquals(5, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeProjectionObjectWithStringIdNodeFallbackForNoPk() throws Exception {
        // Exercises the deserializeIdFromNode node.isTextual() path.
        record NoPkStringProjection(String id, @Nonnull String value)
                implements Projection<String> {}
        record NoPkStringProjectionHolder(@Nullable Ref<NoPkStringProjection> data) {}
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"data\":{\"@id\":\"abc\",\"@projection\":{\"id\":\"abc\",\"value\":\"hello\"}}}";
        var holder = mapper.readValue(json, NoPkStringProjectionHolder.class);
        assertNotNull(holder.data());
        assertTrue(holder.data().isLoaded());
        assertEquals("abc", holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeProjectionObjectWithLongIdNodeFallbackForNoPk() throws Exception {
        // Exercises the deserializeIdFromNode node.isLong() path.
        record NoPkLongProjection(Long id, @Nonnull String value)
                implements Projection<Long> {}
        record NoPkLongProjectionHolder(@Nullable Ref<NoPkLongProjection> data) {}
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        long largeValue = (long) Integer.MAX_VALUE + 1L;
        var json = "{\"data\":{\"@id\":" + largeValue
                + ",\"@projection\":{\"id\":" + largeValue + ",\"value\":\"hello\"}}}";
        var holder = mapper.readValue(json, NoPkLongProjectionHolder.class);
        assertNotNull(holder.data());
        assertTrue(holder.data().isLoaded());
        assertEquals(largeValue, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeProjectionObjectWithDoubleIdNodeFallbackForNoPk() throws Exception {
        // Exercises the deserializeIdFromNode node.isDouble() path.
        record NoPkDoubleProjection(Double id, @Nonnull String value)
                implements Projection<Double> {}
        record NoPkDoubleProjectionHolder(@Nullable Ref<NoPkDoubleProjection> data) {}
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"data\":{\"@id\":3.14,\"@projection\":{\"id\":3.14,\"value\":\"hello\"}}}";
        var holder = mapper.readValue(json, NoPkDoubleProjectionHolder.class);
        assertNotNull(holder.data());
        assertTrue(holder.data().isLoaded());
        assertEquals(3.14, holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void constructorWithRefFactoryShouldPassThroughToSupplier() throws Exception {
        RefFactory factory = new RefFactory() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends st.orm.Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID id) {
                return (Ref<T>) Ref.of((Class) type, id);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends st.orm.Data, ID> Ref<T> create(@Nonnull T record, @Nonnull ID id) {
                return (Ref<T>) Ref.of((Class) record.getClass(), id);
            }
        };
        var module = new StormModule(factory);
        var mapper = JsonMapper.builder()
                .addModule(module)
                .build();
        var holder = mapper.readValue("{\"entity\":123}", EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(123, holder.entity().id());
    }

    // Additional record types for broader coverage

    public record StringIdEntity(@PK String id, @Nonnull String name) implements Entity<String> {}

    public record LongIdEntity(@PK Long id, @Nonnull String name) implements Entity<Long> {}

    public record DoubleIdEntity(@PK Double id, @Nonnull String name) implements Entity<Double> {}

    public record StringIdHolder(@Nonnull Ref<StringIdEntity> entity) {}

    public record LongIdHolder(@Nonnull Ref<LongIdEntity> entity) {}

    public record DoubleIdHolder(@Nonnull Ref<DoubleIdEntity> entity) {}

    public record RefSetHolder(@Nonnull Set<Ref<SimpleEntity>> entities) {}

    public record RefMapHolder(@Nonnull Map<String, Ref<SimpleEntity>> entities) {}

    public record NoPkRefListHolder(@Nonnull List<Ref<NoPkData>> data) {}

    public record NoPkProjection(String label) implements Projection<Void> {}

    public record NoPkProjectionHolder(@Nullable Ref<NoPkProjection> projection) {}

    public record DataRefHolder(@Nullable Ref<NoPkData> data) {}

    // Constructor with null RefFactory

    @org.junit.jupiter.api.Test
    void constructorWithNullRefFactoryShouldCreateModuleWithDetachedRefs() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule((RefFactory) null))
                .build();
        var holder = mapper.readValue("{\"entity\":1}", EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
    }

    @org.junit.jupiter.api.Test
    void constructorWithNullSupplierResultShouldCreateDetachedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule(() -> null))
                .build();
        var holder = mapper.readValue("{\"entity\":7}", EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(7, holder.entity().id());
    }

    // Serialization for different ID types

    @org.junit.jupiter.api.Test
    void unloadedRefWithStringIdShouldSerializeAsStringValue() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new StringIdHolder(Ref.of(StringIdEntity.class, "abc-123"));
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":\"abc-123\"}", json);
    }

    @org.junit.jupiter.api.Test
    void unloadedRefWithLongIdShouldSerializeAsLongValue() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new LongIdHolder(Ref.of(LongIdEntity.class, 9999999999L));
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":9999999999}", json);
    }

    // Deserialization for different ID types

    @org.junit.jupiter.api.Test
    void rawLongIdShouldDeserializeToUnloadedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entity\":9999999999}", LongIdHolder.class);
        assertNotNull(holder.entity());
        assertEquals(9999999999L, holder.entity().id());
    }

    // Deserialization error paths

    @org.junit.jupiter.api.Test
    void projectionObjectWithoutIdFieldShouldThrowException() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"projection\":{\"@projection\":{\"id\":5,\"label\":\"Test\"}}}", ProjectionHolder.class));
    }

    @org.junit.jupiter.api.Test
    void booleanTokenShouldThrowExceptionDuringRefDeserialization() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"entity\":true}", EntityHolder.class));
    }

    @org.junit.jupiter.api.Test
    void arrayTokenShouldThrowExceptionDuringRefDeserialization() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"entity\":[1,2,3]}", EntityHolder.class));
    }

    // Set and Map container types

    @org.junit.jupiter.api.Test
    void refInSetShouldDeserializeCorrectly() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entities\":[1,2,3]}", RefSetHolder.class);
        assertEquals(3, holder.entities().size());
    }

    @org.junit.jupiter.api.Test
    void refInMapValueShouldDeserializeCorrectly() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"entities\":{\"a\":1,\"b\":2}}", RefMapHolder.class);
        assertEquals(2, holder.entities().size());
        assertEquals(1, holder.entities().get("a").id());
        assertEquals(2, holder.entities().get("b").id());
    }

    @org.junit.jupiter.api.Test
    void refSetSerializationShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var refs = Set.of(
                Ref.of(SimpleEntity.class, 1),
                Ref.of(SimpleEntity.class, 2));
        var holder = new RefSetHolder(refs);
        var json = mapper.writeValueAsString(holder);
        assertNotNull(json);
        var deserialized = mapper.readValue(json, RefSetHolder.class);
        assertEquals(2, deserialized.entities().size());
    }

    @org.junit.jupiter.api.Test
    void refMapSerializationShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var refs = Map.of(
                "a", Ref.of(SimpleEntity.class, 1),
                "b", Ref.of(SimpleEntity.class, 2));
        var holder = new RefMapHolder(refs);
        var json = mapper.writeValueAsString(holder);
        assertNotNull(json);
    }

    // NoPk projection node fallback paths

    @org.junit.jupiter.api.Test
    void noPkProjectionWithIntIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue(
                "{\"projection\":{\"@id\":42,\"@projection\":{\"label\":\"Test\"}}}",
                NoPkProjectionHolder.class);
        assertNotNull(holder.projection());
        assertEquals(42, holder.projection().id());
    }

    @org.junit.jupiter.api.Test
    void noPkProjectionWithLongIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"projection\":{\"@id\":9999999999,\"@projection\":{\"label\":\"Test\"}}}";
        var holder = mapper.readValue(json, NoPkProjectionHolder.class);
        assertNotNull(holder.projection());
        assertEquals(9999999999L, holder.projection().id());
    }

    @org.junit.jupiter.api.Test
    void noPkProjectionWithDoubleIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue(
                "{\"projection\":{\"@id\":3.14,\"@projection\":{\"label\":\"Test\"}}}",
                NoPkProjectionHolder.class);
        assertNotNull(holder.projection());
        assertEquals(3.14, holder.projection().id());
    }

    @org.junit.jupiter.api.Test
    void noPkProjectionWithStringIdNodeShouldDeserializeViaNodeFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue(
                "{\"projection\":{\"@id\":\"abc\",\"@projection\":{\"label\":\"Test\"}}}",
                NoPkProjectionHolder.class);
        assertNotNull(holder.projection());
        assertEquals("abc", holder.projection().id());
    }

    @org.junit.jupiter.api.Test
    void noPkProjectionWithObjectIdNodeShouldDeserializeViaObjectFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue(
                "{\"projection\":{\"@id\":{\"key\":\"val\"},\"@projection\":{\"label\":\"Test\"}}}",
                NoPkProjectionHolder.class);
        assertNotNull(holder.projection());
        assertNotNull(holder.projection().id());
    }

    // Entity via @projection path and non-Entity via @entity path

    @org.junit.jupiter.api.Test
    void projectionPathWithEntityDataShouldCreateEntityRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var json = "{\"entity\":{\"@id\":1,\"@projection\":{\"id\":1,\"name\":\"Alice\"}}}";
        var holder = mapper.readValue(json, EntityHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
        assertNotNull(holder.entity().getOrNull());
    }

    @org.junit.jupiter.api.Test
    void entityPathWithNonEntityDataShouldThrowException() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"data\":{\"@entity\":{\"id\":42,\"value\":\"test\"}}}", DataRefHolder.class));
    }

    @org.junit.jupiter.api.Test
    void projectionPathWithPlainDataAndIdShouldCreateUnloadedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue(
                "{\"data\":{\"@id\":42,\"@projection\":{\"id\":42,\"value\":\"test\"}}}",
                DataRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    // NoPk data serialization and list tests

    @org.junit.jupiter.api.Test
    void unloadedNoPkRefShouldSerializeAsRawId() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new NoPkDataHolder(Ref.of(NoPkData.class, 42));
        var json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("42"));
    }

    @org.junit.jupiter.api.Test
    void noPkDataSerializerShouldHandleMissingPkTypeGracefully() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new DataRefHolder(Ref.of(NoPkData.class, "text-id"));
        var json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("text-id"));
    }

    @org.junit.jupiter.api.Test
    void noPkRefListWithMixedIdTypesShouldDeserializeViaFallback() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"data\":[1,\"two\",3]}", NoPkRefListHolder.class);
        assertEquals(3, holder.data().size());
        assertEquals(1, ((Number) holder.data().get(0).id()).intValue());
        assertEquals("two", holder.data().get(1).id());
        assertEquals(3, ((Number) holder.data().get(2).id()).intValue());
    }

    @org.junit.jupiter.api.Test
    void noPkRefWithNullIdInListShouldReturnNullRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"data\":[null, 1]}", NoPkRefListHolder.class);
        assertEquals(2, holder.data().size());
        assertNull(holder.data().get(0));
        assertNotNull(holder.data().get(1));
    }

    // Double ID round-trip

    @org.junit.jupiter.api.Test
    void doubleIdRefShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new DoubleIdHolder(Ref.of(DoubleIdEntity.class, 3.14));
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"entity\":3.14}", json);
        var deserialized = mapper.readValue(json, DoubleIdHolder.class);
        assertEquals(3.14, deserialized.entity().id());
    }

    @org.junit.jupiter.api.Test
    void loadedDoubleIdEntityRefShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var entity = new DoubleIdEntity(2.71, "Euler");
        var holder = new DoubleIdHolder(Ref.of(entity));
        var json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("@entity"));
        var deserialized = mapper.readValue(json, DoubleIdHolder.class);
        assertEquals(2.71, deserialized.entity().id());
    }

    // Additional round-trip tests

    @org.junit.jupiter.api.Test
    void stringIdRefShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new StringIdHolder(Ref.of(StringIdEntity.class, "my-id"));
        var json = mapper.writeValueAsString(holder);
        var deserialized = mapper.readValue(json, StringIdHolder.class);
        assertEquals(holder, deserialized);
    }

    @org.junit.jupiter.api.Test
    void loadedStringIdEntityRefShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var entity = new StringIdEntity("abc", "Test");
        var holder = new StringIdHolder(Ref.of(entity));
        var json = mapper.writeValueAsString(holder);
        var deserialized = mapper.readValue(json, StringIdHolder.class);
        assertEquals(holder, deserialized);
    }

    @org.junit.jupiter.api.Test
    void loadedLongIdEntityRefShouldRoundTrip() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var entity = new LongIdEntity(999L, "Test");
        var holder = new LongIdHolder(Ref.of(entity));
        var json = mapper.writeValueAsString(holder);
        var deserialized = mapper.readValue(json, LongIdHolder.class);
        assertEquals(holder, deserialized);
    }

    // Null projection ref

    @org.junit.jupiter.api.Test
    void nullProjectionRefShouldSerializeToNull() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = new ProjectionHolder(null);
        var json = mapper.writeValueAsString(holder);
        assertEquals("{\"projection\":null}", json);
    }

    @org.junit.jupiter.api.Test
    void nullProjectionRefShouldDeserializeFromNull() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        var holder = mapper.readValue("{\"projection\":null}", ProjectionHolder.class);
        assertNull(holder.projection());
    }

    // Raw Ref without type info

    @SuppressWarnings("rawtypes")
    @org.junit.jupiter.api.Test
    void rawRefWithoutTypeInfoShouldThrowOnDeserialization() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("42", Ref.class));
    }

    // NoPk boolean error

    @org.junit.jupiter.api.Test
    void noPkDataWithBooleanShouldThrowExceptionViaFallbackPath() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"data\":true}", NoPkDataHolder.class));
    }

    public record SimpleNoPkData(String value) implements Data {}

    public record EntityRefHolder(@Nonnull Ref<SimpleEntity> entity) {}

    public record NullableEntityRefHolder(@Nullable Ref<SimpleEntity> entity) {}

    public record ProjectionRefHolder(@Nullable Ref<SimpleProjection> projection) {}

    public record SimpleNoPkRefHolder(@Nullable Ref<SimpleNoPkData> data) {}

    public record RefListHolder(@Nonnull List<Ref<SimpleEntity>> entities) {}

    @org.junit.jupiter.api.Test
    void serializerShouldHandleDirectRefSerializationWithoutProperty() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        Ref<SimpleEntity> ref = Ref.of(SimpleEntity.class, 42);
        String json = mapper.writeValueAsString(ref);
        assertNotNull(json);
        assertTrue(json.contains("42"));
    }

    @org.junit.jupiter.api.Test
    void nonRefFieldShouldNotAffectSerialization() throws Exception {
        record NonRefHolder(@Nonnull String value) {}

        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        NonRefHolder holder = new NonRefHolder("test");
        String json = mapper.writeValueAsString(holder);
        assertEquals("{\"value\":\"test\"}", json);
    }

    @org.junit.jupiter.api.Test
    void refListWithLoadedEntitiesAndNullsShouldDeserializeCorrectly() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entities\":[null,1,{\"@entity\":{\"id\":2,\"name\":\"Bob\"}}]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, holder.entities().size());
        assertNull(holder.entities().get(0));
        assertEquals(1, holder.entities().get(1).id());
        assertEquals(2, holder.entities().get(2).id());
        assertNotNull(holder.entities().get(2).getOrNull());
    }

    @org.junit.jupiter.api.Test
    void supplierReturningNullFactoryShouldCreateDetachedRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule(() -> null))
                .build();
        String json = "{\"entity\":42}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
        assertNull(holder.entity().getOrNull());
    }

    @org.junit.jupiter.api.Test
    void defaultConstructorShouldCreateModuleWithDetachedRefs() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entity\":99}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(99, holder.entity().id());
        assertNull(holder.entity().getOrNull());
    }

    @org.junit.jupiter.api.Test
    void projectionWithEntityTypeInProjectionPathShouldCreateEntityRef() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entity\":{\"@id\":1,\"@projection\":{\"id\":1,\"name\":\"Via Projection Path\"}}}";
        EntityRefHolder holder = mapper.readValue(json, EntityRefHolder.class);
        assertNotNull(holder.entity());
        assertEquals(1, holder.entity().id());
        SimpleEntity loaded = holder.entity().getOrNull();
        assertNotNull(loaded);
        assertEquals("Via Projection Path", loaded.name());
    }

    @org.junit.jupiter.api.Test
    void refFactorySupplierShouldBeCalledDuringDeserialization() throws Exception {
        var factoryCallCount = new int[]{0};
        RefFactory factory = new RefFactory() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull Class<T> type, @Nonnull ID pk) {
                factoryCallCount[0]++;
                return (Ref<T>) Ref.of((Class) type, pk);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T extends Data, ID> Ref<T> create(@Nonnull T record, @Nonnull ID pk) {
                return (Ref<T>) Ref.of((Class) record.getClass(), pk);
            }
        };
        var mapper = JsonMapper.builder()
                .addModule(new StormModule(() -> factory))
                .build();
        String json = "{\"entities\":[1,2,3]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertEquals(3, holder.entities().size());
        assertEquals(3, factoryCallCount[0]);
    }

    @org.junit.jupiter.api.Test
    void simpleNoPkDataWithProjectionFormatAndNullIdShouldThrow() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@id\":null,\"@projection\":{\"value\":\"test\"}}}";
        assertThrows(DatabindException.class, () ->
                mapper.readValue(json, SimpleNoPkRefHolder.class));
    }

    public record SimpleNoPkDataProjectionHolder(@Nullable Ref<SimpleNoPkData> data) {}

    @org.junit.jupiter.api.Test
    void simpleNoPkDataWithProjectionFormatAndNonNullIdShouldCreateRefWithId() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@id\":42,\"@projection\":{\"value\":\"test\"}}}";
        SimpleNoPkDataProjectionHolder holder = mapper.readValue(json, SimpleNoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertEquals(42, ((Number) holder.data().id()).intValue());
    }

    @org.junit.jupiter.api.Test
    void emptyRefListShouldDeserializeCorrectly() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entities\":[]}";
        RefListHolder holder = mapper.readValue(json, RefListHolder.class);
        assertNotNull(holder.entities());
        assertEquals(0, holder.entities().size());
    }

    @org.junit.jupiter.api.Test
    void loadedEntityRefInListShouldSerializeAndDeserialize() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
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

    public record ProjectionRefListHolder(@Nonnull List<Ref<SimpleProjection>> projections) {}

    @org.junit.jupiter.api.Test
    void loadedProjectionRefInListShouldSerializeAndDeserialize() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
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

    @org.junit.jupiter.api.Test
    void multipleRefTypesInSameHolderShouldDeserializeCorrectly() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entity\":42,\"projection\":7}";
        MultiTypeHolder holder = mapper.readValue(json, MultiTypeHolder.class);
        assertNotNull(holder.entity());
        assertEquals(42, holder.entity().id());
        assertNotNull(holder.projection());
        assertEquals(7, holder.projection().id());
    }

    @org.junit.jupiter.api.Test
    void entityPathWithSimpleNoPkDataShouldThrowException() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@entity\":{\"value\":\"test\"}}}";
        assertThrows(DatabindException.class, () ->
                mapper.readValue(json, SimpleNoPkDataProjectionHolder.class));
    }

    @org.junit.jupiter.api.Test
    void simpleNoPkDataSerializerShouldHandleMissingPkTypeGracefully() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        Ref<SimpleNoPkData> ref = Ref.of(SimpleNoPkData.class, "text-id");
        SimpleNoPkDataProjectionHolder holder = new SimpleNoPkDataProjectionHolder(ref);
        String json = mapper.writeValueAsString(holder);
        assertTrue(json.contains("text-id"));
    }

    @org.junit.jupiter.api.Test
    void deserializeIdWithFloatValueForSimpleNoPkShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":3.14}";
        SimpleNoPkRefHolder holder = mapper.readValue(json, SimpleNoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(3.14, ((Number) holder.data().id()).doubleValue(), 0.001);
    }

    @org.junit.jupiter.api.Test
    void deserializeIdWithStringValueForSimpleNoPkShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":\"abc-123\"}";
        SimpleNoPkRefHolder holder = mapper.readValue(json, SimpleNoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals("abc-123", holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void deserializeIdWithLongValueForSimpleNoPkShouldReturnLong() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":3000000000}";
        SimpleNoPkRefHolder holder = mapper.readValue(json, SimpleNoPkRefHolder.class);
        assertNotNull(holder.data());
        assertEquals(3000000000L, ((Number) holder.data().id()).longValue());
    }

    @org.junit.jupiter.api.Test
    void deserializeIdFromNodeWithLongValueForSimpleNoPkShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@id\":3000000000,\"@projection\":{\"value\":\"test\"}}}";
        SimpleNoPkDataProjectionHolder holder = mapper.readValue(json, SimpleNoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertEquals(3000000000L, ((Number) holder.data().id()).longValue());
    }

    @org.junit.jupiter.api.Test
    void deserializeIdFromNodeWithDoubleValueForSimpleNoPkShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@id\":2.71,\"@projection\":{\"value\":\"test\"}}}";
        SimpleNoPkDataProjectionHolder holder = mapper.readValue(json, SimpleNoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertEquals(2.71, ((Number) holder.data().id()).doubleValue(), 0.001);
    }

    @org.junit.jupiter.api.Test
    void deserializeIdFromNodeWithStringValueForSimpleNoPkShouldWork() throws Exception {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"data\":{\"@id\":\"uuid-value\",\"@projection\":{\"value\":\"test\"}}}";
        SimpleNoPkDataProjectionHolder holder = mapper.readValue(json, SimpleNoPkDataProjectionHolder.class);
        assertNotNull(holder.data());
        assertEquals("uuid-value", holder.data().id());
    }

    @org.junit.jupiter.api.Test
    void projectionWithoutIdFieldShouldThrow() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"projection\":{\"@projection\":{\"id\":1,\"label\":\"test\"}}}";
        assertThrows(DatabindException.class, () ->
                mapper.readValue(json, ProjectionRefHolder.class));
    }

    @org.junit.jupiter.api.Test
    void refObjectWithoutEntityOrProjectionFieldShouldThrow() {
        var mapper = JsonMapper.builder()
                .addModule(new StormModule())
                .build();
        String json = "{\"entity\":{\"unknown\":\"value\"}}";
        assertThrows(DatabindException.class, () ->
                mapper.readValue(json, EntityRefHolder.class));
    }
}
