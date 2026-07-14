/*
 * Copyright 2024 - 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.core.repository.impl;

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import st.orm.Data;
import st.orm.Entity;
import st.orm.FK;
import st.orm.GenerationStrategy;
import st.orm.PK;
import st.orm.Persist;
import st.orm.PersistenceException;
import st.orm.Ref;
import st.orm.WriteSet;
import st.orm.core.repository.EntityRepository;
import st.orm.core.repository.RepositoryLookup;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

/**
 * Default implementation of {@link WriteSet}.
 *
 * <p>Resolves the dependency graph of the passed entities, partitions it into topological levels, and delegates each
 * level to the existing per-type repository batch operations. Generated primary keys are propagated to dependent
 * records by rebuilding the immutable records with the keyed instances, correlated by instance identity. For
 * non-insertable FK components whose column value is carried by a component of the primary key (the junction table
 * pattern), the generated key is additionally written into the carrying key component.</p>
 *
 * <p>Instances are stateless per call (all resolution state is method-local; the per-type metadata cache is
 * concurrent) and can safely be shared across threads.</p>
 *
 * @since 1.13
 */
public final class WriteSetImpl implements WriteSet {

    private static final ORMReflection REFLECTION = Providers.getORMReflection();

    /** How the write set treats entities that are reachable but not part of the set. */
    private enum Verb { INSERT, UPSERT, UPDATE, REMOVE }

    private final RepositoryLookup lookup;
    private final ConcurrentMap<Class<?>, TypeInfo> typeInfoCache = new ConcurrentHashMap<>();

    public WriteSetImpl(@Nonnull RepositoryLookup lookup) {
        this.lookup = requireNonNull(lookup, "lookup");
    }

    @Override
    public void insert(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeOrdered(entities, Verb.INSERT, false);
    }

    @Override
    @Nonnull
    public List<Entity<?>> insertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities) {
        Execution execution = executeOrdered(entities, Verb.INSERT, true);
        return fetch(execution);
    }

    @Override
    public void upsert(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeOrdered(entities, Verb.UPSERT, false);
    }

    @Override
    @Nonnull
    public List<Entity<?>> upsertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities) {
        Execution execution = executeOrdered(entities, Verb.UPSERT, true);
        return fetch(execution);
    }

    @Override
    public void update(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeUpdate(entities);
    }

    @Override
    @Nonnull
    public List<Entity<?>> updateAndFetch(@Nonnull Iterable<? extends Entity<?>> entities) {
        Execution execution = executeUpdate(entities);
        return fetch(execution);
    }

    @Override
    public void remove(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeRemove(entities);
    }

    //
    // Graph resolution and execution for insert / upsert.
    //

    /**
     * The outcome of an executed write set: the passed entities in input order and the persisted view of every node,
     * keyed by instance identity, for id resolution during fetch.
     */
    private record Execution(List<Object> inputs, IdentityHashMap<Object, Object> persistedView) {}

    /**
     * A node of the resolved graph: the entity, whether it was passed explicitly, its unsaved dependencies (which
     * require key propagation) and its ordering-only dependencies on other set members (keyed rows that must be
     * written first, correlated by primary key).
     */
    static final class Node {
        final Object entity;
        boolean passed;
        final List<Dependency> dependencies = new ArrayList<>(4);
        final List<Node> orderingDependencies = new ArrayList<>(2);
        int level = -1;

        Node(Object entity, boolean passed) {
            this.entity = entity;
            this.passed = passed;
        }
    }

    /** An unsaved entity referenced through an FK component, together with the component to rebuild. */
    private record Dependency(FkEdge edge, Object target) {}

    private Execution executeOrdered(@Nonnull Iterable<? extends Entity<?>> entities, @Nonnull Verb verb,
                                     boolean fetchKeys) {
        List<Object> inputs = new ArrayList<>();
        entities.forEach(inputs::add);
        // Discover the insertion closure: explicit members plus unsaved entities transitively reachable through
        // insertable foreign key fields.
        IdentityHashMap<Object, Node> nodes = new IdentityHashMap<>();
        List<Node> discoveryOrder = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Object input : inputs) {
            Node node = nodes.get(input);
            if (node == null) {
                node = new Node(input, true);
                nodes.put(input, node);
                discoveryOrder.add(node);
                queue.add(node);
            } else {
                node.passed = true;
            }
        }
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            TypeInfo info = typeInfo(node.entity.getClass());
            for (FkEdge edge : info.fkEdges) {
                Object target = resolveTarget(node.entity, edge, verb);
                if (target == null || !isUnsaved(target)) {
                    continue;
                }
                if (!edge.insertable && edge.keyPath == null) {
                    throw new PersistenceException(("Foreign key component '%s.%s' is not insertable but references " +
                            "an unsaved %s, and no insertable primary key component carries its column value, so " +
                            "the write set cannot propagate the generated key. Persist the %s first and set its id " +
                            "explicitly.").formatted(
                                    node.entity.getClass().getSimpleName(), edge.name,
                                    target.getClass().getSimpleName(), target.getClass().getSimpleName()));
                }
                Node targetNode = nodes.get(target);
                if (targetNode == null) {
                    targetNode = new Node(target, false);
                    nodes.put(target, targetNode);
                    discoveryOrder.add(targetNode);
                    queue.add(targetNode);
                }
                node.dependencies.add(new Dependency(edge, target));
            }
        }
        // Members with a preserved primary key may be referenced by other members through their key rather than by
        // instance. Such references carry no key propagation, but the referenced row must be written first to
        // satisfy foreign key constraints. A key is preserved when it is not generated, or when the member receives
        // upsert semantics: an upsert matches on the provided key instead of generating a new one, so an explicitly
        // passed keyed member of an upsert set is orderable even when its key column is auto-generated. A member
        // whose key propagation still writes into its primary key (a junction row awaiting a parent's generated
        // key) carries a transient key and is not registered.
        Map<TypeIdKey, Node> keyedMembers = new HashMap<>();
        for (Node node : discoveryOrder) {
            TypeInfo info = typeInfo(node.entity.getClass());
            boolean keyPreserved = !info.autoGeneratedPrimaryKey || (verb == Verb.UPSERT && node.passed);
            if (keyPreserved && node.dependencies.stream().noneMatch(dependency ->
                    writesPrimaryKey(info, dependency.edge()))) {
                Object id = ((Entity<?>) node.entity).id();
                if (!REFLECTION.isDefaultValue(id)) {
                    keyedMembers.put(new TypeIdKey(node.entity.getClass(), id), node);
                }
            }
        }
        if (!keyedMembers.isEmpty()) {
            for (Node node : discoveryOrder) {
                TypeInfo info = typeInfo(node.entity.getClass());
                for (FkEdge edge : info.fkEdges) {
                    Object targetId = resolveTargetId(node.entity, edge);
                    if (targetId == null || REFLECTION.isDefaultValue(targetId)) {
                        continue;
                    }
                    Node keyedMember = keyedMembers.get(new TypeIdKey(edge.targetType, targetId));
                    if (keyedMember != null && keyedMember != node) {
                        node.orderingDependencies.add(keyedMember);
                    }
                }
            }
        }
        assignLevels(nodes, discoveryOrder);
        // A node's generated key is only fetched when something consumes it: a dependent node (key propagation) or
        // the caller (AndFetch). Everything else is written without fetch mode, keeping the write compatible with
        // dialects that cannot return generated keys for every generation strategy.
        IdentityHashMap<Object, Boolean> keyConsumers = new IdentityHashMap<>();
        for (Node node : discoveryOrder) {
            for (Dependency dependency : node.dependencies) {
                keyConsumers.put(dependency.target(), Boolean.TRUE);
            }
        }
        // Execute level by level, batched per type. Discovered members are always inserted; explicit members are
        // inserted or upserted according to the verb.
        IdentityHashMap<Object, Object> persistedView = new IdentityHashMap<>();
        for (Map<Class<?>, List<Node>> byType : groupByLevelAndType(discoveryOrder)) {
            for (var entry : byType.entrySet()) {
                TypeInfo info = typeInfo(entry.getKey());
                if (verb == Verb.UPSERT) {
                    // Discovered members are inserted; only explicit members carry upsert semantics.
                    persistBatch(info, entry.getValue().stream().filter(node -> !node.passed).toList(),
                            persistedView, Verb.INSERT, fetchKeys, keyConsumers);
                    persistBatch(info, entry.getValue().stream().filter(node -> node.passed).toList(),
                            persistedView, Verb.UPSERT, fetchKeys, keyConsumers);
                } else {
                    persistBatch(info, entry.getValue(), persistedView, Verb.INSERT, fetchKeys, keyConsumers);
                }
            }
        }
        return new Execution(inputs, persistedView);
    }

    /** Groups the nodes into per-level maps of type to nodes, preserving discovery order within each group. */
    private static List<Map<Class<?>, List<Node>>> groupByLevelAndType(@Nonnull List<Node> nodes) {
        int maxLevel = nodes.stream().mapToInt(node -> node.level).max().orElse(-1);
        List<Map<Class<?>, List<Node>>> levels = new ArrayList<>(maxLevel + 1);
        for (int level = 0; level <= maxLevel; level++) {
            levels.add(new LinkedHashMap<>());
        }
        for (Node node : nodes) {
            levels.get(node.level).computeIfAbsent(node.entity.getClass(), type -> new ArrayList<>()).add(node);
        }
        return levels;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void persistBatch(@Nonnull TypeInfo info,
                              @Nonnull List<Node> nodes,
                              @Nonnull IdentityHashMap<Object, Object> persistedView,
                              @Nonnull Verb verb,
                              boolean fetchKeys,
                              @Nonnull IdentityHashMap<Object, Boolean> keyConsumers) {
        if (nodes.isEmpty()) {
            return;
        }
        List<Object> prepared = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            prepared.add(propagateKeys(node, persistedView));
        }
        EntityRepository repository = info.repository;
        boolean keysNeeded = info.autoGeneratedPrimaryKey && nodes.stream().anyMatch(
                node -> keyConsumers.containsKey(node.entity) || (fetchKeys && node.passed));
        if (keysNeeded) {
            // The fetch-ids operations return the ids in input order; the same positional contract is relied upon
            // by the repository implementations themselves (see JoinedEntityHelper).
            List<?> ids = verb == Verb.UPSERT
                    ? repository.upsertAndFetchIds(prepared)
                    : repository.insertAndFetchIds(prepared);
            for (int i = 0; i < nodes.size(); i++) {
                persistedView.put(nodes.get(i).entity, withPrimaryKey(info, prepared.get(i), ids.get(i)));
            }
        } else {
            if (verb == Verb.UPSERT) {
                repository.upsert(prepared);
            } else {
                repository.insert(prepared);
            }
            for (int i = 0; i < nodes.size(); i++) {
                persistedView.put(nodes.get(i).entity, prepared.get(i));
            }
        }
    }

    /** Rebuilds the node's entity with every unsaved FK reference replaced by its persisted counterpart. */
    private Object propagateKeys(@Nonnull Node node, @Nonnull IdentityHashMap<Object, Object> persistedView) {
        Object entity = node.entity;
        for (Dependency dependency : node.dependencies) {
            Object persisted = persistedView.get(dependency.target());
            if (persisted == null) {
                // Level ordering guarantees dependencies are persisted first; this indicates an internal error.
                throw new PersistenceException("Internal error: dependency %s of %s has not been persisted."
                        .formatted(dependency.target().getClass().getSimpleName(),
                                entity.getClass().getSimpleName()));
            }
            FkEdge edge = dependency.edge();
            // Wrapping the persisted instance preserves its concrete type (which may be a subtype of the declared
            // component type) and hands dependents a loaded ref rather than an id-only one.
            Object newValue = edge.ref
                    ? Ref.of((Entity<?>) persisted)
                    : persisted;
            entity = withComponent(entity, edge.path, 0, newValue);
            if (edge.keyPath != null) {
                // The component's column value is carried by the primary key; write the generated key into the
                // carrier so the insert binds it.
                entity = withComponent(entity, edge.keyPath, 0, ((Entity<?>) persisted).id());
            }
        }
        return entity;
    }

    /**
     * Determines whether propagating the given edge rewrites the entity's primary key: either the edge itself is a
     * primary key component (a key that is also a foreign key) or its generated key is carried by a primary key
     * component (a junction row).
     */
    private static boolean writesPrimaryKey(@Nonnull TypeInfo info, @Nonnull FkEdge edge) {
        if (info.primaryKeyIndex < 0) {
            return false;
        }
        return edge.path[0] == info.primaryKeyIndex
                || (edge.keyPath != null && edge.keyPath[0] == info.primaryKeyIndex);
    }

    //
    // Update.
    //

    private Execution executeUpdate(@Nonnull Iterable<? extends Entity<?>> entities) {
        List<Object> inputs = new ArrayList<>();
        Map<Class<?>, List<Object>> byType = new LinkedHashMap<>();
        for (Entity<?> entity : entities) {
            if (isUnsaved(entity)) {
                throw new PersistenceException(("Cannot update unsaved %s. Its primary key is the default value; " +
                        "insert it instead, or use upsert.").formatted(entity.getClass().getSimpleName()));
            }
            inputs.add(entity);
            byType.computeIfAbsent(entity.getClass(), type -> new ArrayList<>()).add(entity);
        }
        for (var entry : byType.entrySet()) {
            update(typeInfo(entry.getKey()).repository, entry.getValue());
        }
        IdentityHashMap<Object, Object> persistedView = new IdentityHashMap<>();
        for (Object input : inputs) {
            persistedView.put(input, input);
        }
        return new Execution(inputs, persistedView);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void update(@Nonnull EntityRepository repository, @Nonnull List<Object> batch) {
        repository.update((Iterable) batch);
    }

    //
    // Remove.
    //

    private void executeRemove(@Nonnull Iterable<? extends Entity<?>> entities) {
        // Members are correlated by primary key rather than instance identity: two instances describing the same row
        // are removed once, and dependencies hold regardless of which instance a member embeds.
        Map<TypeIdKey, Object> members = new LinkedHashMap<>();
        for (Entity<?> entity : entities) {
            if (isUnsaved(entity)) {
                throw new PersistenceException(("Cannot remove unsaved %s. Its primary key is the default value, " +
                        "so it does not describe a database row.").formatted(entity.getClass().getSimpleName()));
            }
            members.putIfAbsent(new TypeIdKey(entity.getClass(), entity.id()), entity);
        }
        // Build member-to-member dependencies via FK values; children must be removed before their parents.
        IdentityHashMap<Object, Node> nodes = new IdentityHashMap<>();
        List<Node> order = new ArrayList<>();
        for (Object member : members.values()) {
            Node node = new Node(member, true);
            nodes.put(member, node);
            order.add(node);
        }
        for (Node node : order) {
            TypeInfo info = typeInfo(node.entity.getClass());
            for (FkEdge edge : info.fkEdges) {
                Object targetId = resolveTargetId(node.entity, edge);
                if (targetId == null) {
                    continue;
                }
                Object parent = members.get(new TypeIdKey(edge.targetType, targetId));
                if (parent != null && parent != node.entity) {
                    node.orderingDependencies.add(nodes.get(parent));
                }
            }
        }
        assignLevels(nodes, order);
        // Reverse level order: the deepest dependents go first, their referenced members last.
        List<Map<Class<?>, List<Node>>> levels = groupByLevelAndType(order);
        for (int level = levels.size() - 1; level >= 0; level--) {
            for (var entry : levels.get(level).entrySet()) {
                remove(typeInfo(entry.getKey()).repository,
                        entry.getValue().stream().map(node -> node.entity).toList());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void remove(@Nonnull EntityRepository repository, @Nonnull List<Object> batch) {
        repository.remove((Iterable) batch);
    }

    /** Key correlating remove members by row rather than by instance. */
    private record TypeIdKey(Class<?> type, Object id) {}

    //
    // Shared machinery.
    //

    /**
     * Assigns each node the length of its longest dependency chain, so that a node always lands on a higher level
     * than everything it depends on. Fails fast when a dependency cycle prevents an ordering.
     */
    static void assignLevels(@Nonnull IdentityHashMap<Object, Node> nodes, @Nonnull List<Node> order) {
        List<Node> remaining = new ArrayList<>(order);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            List<Node> next = new ArrayList<>();
            for (Node node : remaining) {
                int level = 0;
                boolean ready = true;
                for (Dependency dependency : node.dependencies) {
                    Node dependencyNode = nodes.get(dependency.target());
                    if (dependencyNode.level < 0) {
                        ready = false;
                        break;
                    }
                    level = Math.max(level, dependencyNode.level + 1);
                }
                for (Node dependencyNode : node.orderingDependencies) {
                    if (!ready) {
                        break;
                    }
                    if (dependencyNode.level < 0) {
                        ready = false;
                        break;
                    }
                    level = Math.max(level, dependencyNode.level + 1);
                }
                if (ready) {
                    node.level = level;
                    progressed = true;
                } else {
                    next.add(node);
                }
            }
            if (!progressed) {
                String cycle = next.stream()
                        .limit(8)
                        .map(node -> "%s@%08x".formatted(node.entity.getClass().getSimpleName(),
                                System.identityHashCode(node.entity)))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                throw new PersistenceException(("Cannot determine a valid write order: the foreign key dependencies " +
                        "among [%s] form a cycle. Break the cycle by persisting one side first, or by referencing " +
                        "one side through an id-only Ref.").formatted(cycle));
            }
            remaining = next;
        }
    }

    /**
     * Resolves the entity instance referenced by the given FK component, or {@code null} when nothing is referenced
     * or the reference is keyed by id only. Id-only refs carrying a default id cannot join the closure and fail fast
     * for insert and upsert.
     */
    @Nullable
    private Object resolveTarget(@Nonnull Object entity, @Nonnull FkEdge edge, @Nonnull Verb verb) {
        Object value = valueAt(entity, edge.path);
        if (value == null) {
            return null;
        }
        if (edge.ref) {
            Ref<?> ref = (Ref<?>) value;
            Object wrapped = ref.getOrNull();
            if (wrapped != null) {
                return wrapped;
            }
            if ((verb == Verb.INSERT || verb == Verb.UPSERT) && REFLECTION.isDefaultValue(ref.id())) {
                throw new PersistenceException(("Foreign key component '%s.%s' holds an id-only Ref with a default " +
                        "id. An id-only Ref cannot describe a new %s; wrap the instance instead: Ref.of(entity).")
                        .formatted(entity.getClass().getSimpleName(), edge.name,
                                edge.targetType.getSimpleName()));
            }
            return null;
        }
        return value;
    }

    /** Resolves the primary key value referenced by the given FK component, or {@code null}. */
    @Nullable
    private Object resolveTargetId(@Nonnull Object entity, @Nonnull FkEdge edge) {
        Object value = valueAt(entity, edge.path);
        if (value == null) {
            return null;
        }
        if (edge.ref) {
            return ((Ref<?>) value).id();
        }
        return ((Entity<?>) value).id();
    }

    private boolean isUnsaved(@Nonnull Object entity) {
        TypeInfo info = typeInfo(entity.getClass());
        return info.autoGeneratedPrimaryKey && isDefaultPrimaryKey(info.model, ((Entity<?>) entity).id());
    }

    @SuppressWarnings("unchecked")
    private static <ID> boolean isDefaultPrimaryKey(@Nonnull Model<?, ID> model, @Nullable Object id) {
        return model.isDefaultPrimaryKey((ID) id);
    }

    @Nullable
    private Object valueAt(@Nonnull Object record, @Nonnull int[] path) {
        Object current = record;
        for (int index : path) {
            if (current == null) {
                return null;
            }
            current = REFLECTION.getRecordValue(current, index);
        }
        return current;
    }

    /**
     * Rebuilds the record with the component at the given path replaced, reconstructing nested records as needed.
     *
     * <p>Intermediate components along the path are never {@code null} here: a dependency is only recorded when
     * {@link #valueAt(Object, int[])} resolved a non-null target through the same path, and records are
     * immutable.</p>
     */
    private Object withComponent(@Nonnull Object record, @Nonnull int[] path, int depth, @Nullable Object newValue) {
        RecordType recordType = REFLECTION.getRecordType(record.getClass());
        List<RecordField> fields = recordType.fields();
        Object[] args = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            args[i] = REFLECTION.getRecordValue(record, i);
        }
        int index = path[depth];
        args[index] = depth == path.length - 1
                ? newValue
                : withComponent(args[index], path, depth + 1, newValue);
        recordType.constructor().setAccessible(true);
        return recordType.newInstance(args);
    }

    /** Rebuilds the entity with the given primary key. */
    private Object withPrimaryKey(@Nonnull TypeInfo info, @Nonnull Object entity, @Nonnull Object pk) {
        return withComponent(entity, new int[] {info.primaryKeyIndex}, 0, pk);
    }

    //
    // Fetch support.
    //

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    private List<Entity<?>> fetch(@Nonnull Execution execution) {
        // Collect the ids of the passed entities per type, preserving first-seen type order.
        Map<Class<?>, List<Object>> idsByType = new LinkedHashMap<>();
        for (Object input : execution.inputs()) {
            Object persisted = execution.persistedView().get(input);
            Object id = ((Entity<?>) requireNonNull(persisted, "persisted view")).id();
            idsByType.computeIfAbsent(input.getClass(), type -> new ArrayList<>()).add(id);
        }
        Map<TypeIdKey, Entity<?>> fetched = new HashMap<>();
        for (var entry : idsByType.entrySet()) {
            EntityRepository repository = typeInfo(entry.getKey()).repository;
            for (Object entity : (List<Object>) repository.findAllById((Iterable) entry.getValue())) {
                Entity<?> fetchedEntity = (Entity<?>) entity;
                fetched.put(new TypeIdKey(entry.getKey(), fetchedEntity.id()), fetchedEntity);
            }
        }
        List<Entity<?>> result = new ArrayList<>(execution.inputs().size());
        for (Object input : execution.inputs()) {
            Object id = ((Entity<?>) execution.persistedView().get(input)).id();
            Entity<?> fetchedEntity = fetched.get(new TypeIdKey(input.getClass(), id));
            if (fetchedEntity == null) {
                throw new PersistenceException("Failed to fetch %s with id %s after write."
                        .formatted(input.getClass().getSimpleName(), id));
            }
            result.add(fetchedEntity);
        }
        return result;
    }

    //
    // Per-type metadata.
    //

    /**
     * An FK component of an entity type: the component path from the root record, its kind and its target type.
     * A non-insertable component whose column value is carried by an insertable component (typically a field of a
     * composite primary key, as in a junction table) records the carrier's path as {@code keyPath}; generated keys
     * propagate into the carrier rather than through the component itself.
     */
    private record FkEdge(int[] path, String name, String fieldPath, boolean ref, Class<? extends Data> targetType,
                          boolean insertable, @Nullable int[] keyPath) {}

    private static final class TypeInfo {
        final Model<?, ?> model;
        @SuppressWarnings("rawtypes")
        final EntityRepository repository;
        final boolean autoGeneratedPrimaryKey;
        final int primaryKeyIndex;
        final List<FkEdge> fkEdges;

        @SuppressWarnings("unchecked")
        TypeInfo(@Nonnull RepositoryLookup lookup, @Nonnull Class<?> type) {
            this.repository = lookup.entity((Class<Entity<Object>>) type);
            this.model = repository.model();
            this.autoGeneratedPrimaryKey = model.declaredColumns().stream()
                    .filter(Column::primaryKey)
                    .anyMatch(column -> column.generation() != GenerationStrategy.NONE);
            RecordType recordType = REFLECTION.getRecordType(type);
            int primaryKeyFieldIndex = -1;
            List<RecordField> fields = recordType.fields();
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).isAnnotationPresent(PK.class)) {
                    primaryKeyFieldIndex = i;
                    break;
                }
            }
            if (autoGeneratedPrimaryKey && primaryKeyFieldIndex < 0) {
                throw new PersistenceException(("Cannot use %s in a write set: its primary key is auto-generated " +
                        "but no @PK component was found to carry the generated key.").formatted(type.getSimpleName()));
            }
            this.primaryKeyIndex = primaryKeyFieldIndex;
            List<FkEdge> edges = new ArrayList<>();
            collectFkEdges(recordType, new ArrayList<>(), new ArrayList<>(), true, edges);
            if (edges.stream().anyMatch(edge -> !edge.insertable)) {
                resolveKeyCarriers(recordType, edges);
            }
            this.fkEdges = List.copyOf(edges);
        }

        /**
         * Resolves, for each non-insertable FK component, the insertable component that carries its column value:
         * the primary key field whose column shares the FK component's column name, as in a junction table whose
         * key columns live inside a composite primary key. Components with a carrier can join the insertion
         * closure; the generated key is propagated into the carrier instead of through the component itself.
         */
        private void resolveKeyCarriers(@Nonnull RecordType recordType, @Nonnull List<FkEdge> edges) {
            if (primaryKeyIndex < 0) {
                return;
            }
            List<Column> columns = model.declaredColumns();
            List<Column> primaryKeyColumns = columns.stream()
                    .filter(Column::primaryKey)
                    .filter(Column::insertable)
                    .toList();
            List<int[]> leafPaths = primaryKeyLeafPaths(recordType.fields().get(primaryKeyIndex), primaryKeyIndex);
            if (leafPaths == null || leafPaths.size() != primaryKeyColumns.size()) {
                return;
            }
            Map<String, int[]> carrierByColumnName = new HashMap<>();
            for (int i = 0; i < primaryKeyColumns.size(); i++) {
                Column column = primaryKeyColumns.get(i);
                RecordField leafField = fieldAt(recordType, leafPaths.get(i));
                // The leaf fields correspond to the primary key columns by declaration order; a type mismatch
                // indicates the correspondence does not hold, in which case no carriers are resolved.
                if (!wrap(leafField.type()).equals(wrap(column.persistedType()))) {
                    return;
                }
                carrierByColumnName.put(column.name(), leafPaths.get(i));
            }
            // Group the FK columns by the component they belong to; a component mapping to multiple columns
            // references a composite key, which cannot be carried by a single primary key field.
            Map<String, List<Column>> foreignKeyColumnsByFieldPath = new HashMap<>();
            for (Column column : columns) {
                if (column.foreignKey()) {
                    foreignKeyColumnsByFieldPath
                            .computeIfAbsent(column.metamodel().fieldPath(), fieldPath -> new ArrayList<>())
                            .add(column);
                }
            }
            for (int i = 0; i < edges.size(); i++) {
                FkEdge edge = edges.get(i);
                if (edge.insertable) {
                    continue;
                }
                List<Column> edgeColumns = foreignKeyColumnsByFieldPath.get(edge.fieldPath);
                if (edgeColumns == null || edgeColumns.size() != 1) {
                    continue;
                }
                int[] carrier = carrierByColumnName.get(edgeColumns.getFirst().name());
                if (carrier != null) {
                    edges.set(i, new FkEdge(edge.path, edge.name, edge.fieldPath, edge.ref, edge.targetType,
                            false, carrier));
                }
            }
        }
    }

    /**
     * Returns the paths of the scalar leaf components of the primary key field, in declaration order, or
     * {@code null} when the key contains entity, ref or FK components: such keys carry their own edges and need
     * no carrier resolution.
     */
    @Nullable
    private static List<int[]> primaryKeyLeafPaths(@Nonnull RecordField primaryKeyField, int primaryKeyIndex) {
        List<int[]> leaves = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        path.add(primaryKeyIndex);
        if (!collectScalarLeaves(primaryKeyField, path, leaves)) {
            return null;
        }
        return leaves;
    }

    private static boolean collectScalarLeaves(@Nonnull RecordField field,
                                               @Nonnull List<Integer> path,
                                               @Nonnull List<int[]> leaves) {
        if (field.isAnnotationPresent(FK.class)
                || Entity.class.isAssignableFrom(field.type())
                || Ref.class.isAssignableFrom(field.type())) {
            return false;
        }
        var nested = REFLECTION.findRecordType(field.type());
        if (nested.isEmpty()) {
            leaves.add(toArray(path));
            return true;
        }
        List<RecordField> fields = nested.get().fields();
        for (int i = 0; i < fields.size(); i++) {
            path.add(i);
            boolean scalar = collectScalarLeaves(fields.get(i), path, leaves);
            path.remove(path.size() - 1);
            if (!scalar) {
                return false;
            }
        }
        return true;
    }

    /** Returns the record field at the given component path, descending through nested records. */
    private static RecordField fieldAt(@Nonnull RecordType rootType, @Nonnull int[] path) {
        RecordType current = rootType;
        RecordField field = null;
        for (int index : path) {
            field = current.fields().get(index);
            current = REFLECTION.findRecordType(field.type()).orElse(null);
        }
        return requireNonNull(field, "field");
    }

    /** Returns the wrapper type for primitives, the type itself otherwise. */
    private static Class<?> wrap(@Nonnull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return MethodType.methodType(type).wrap().returnType();
    }

    private TypeInfo typeInfo(@Nonnull Class<?> type) {
        return typeInfoCache.computeIfAbsent(type, key -> new TypeInfo(lookup, key));
    }

    /**
     * Collects the FK components of the given record type, recursing into inline records. The {@code insertable}
     * flag tracks inherited {@code @Persist} semantics: an inline component marked non-insertable propagates to its
     * children.
     */
    private static void collectFkEdges(@Nonnull RecordType recordType,
                                @Nonnull List<Integer> path,
                                @Nonnull List<String> nameParts,
                                boolean insertable,
                                @Nonnull List<FkEdge> edges) {
        List<RecordField> fields = recordType.fields();
        for (int i = 0; i < fields.size(); i++) {
            RecordField field = fields.get(i);
            Persist persist = field.getAnnotation(Persist.class);
            boolean fieldInsertable = insertable && (persist == null || persist.insertable());
            if (field.isAnnotationPresent(FK.class)) {
                path.add(i);
                nameParts.add(field.name());
                String fieldPath = String.join(".", nameParts);
                if (Ref.class.isAssignableFrom(field.type())) {
                    // Only refs to entities can act as write-set edges; refs to projections merely bind their id.
                    Class<? extends Data> targetType = refTargetType(field);
                    if (Entity.class.isAssignableFrom(targetType)) {
                        edges.add(new FkEdge(toArray(path), field.name(), fieldPath, true, targetType,
                                fieldInsertable, null));
                    }
                } else if (field.isDataType() && Entity.class.isAssignableFrom(field.type())) {
                    edges.add(new FkEdge(toArray(path), field.name(), fieldPath, false,
                            (Class<? extends Data>) field.type(), fieldInsertable, null));
                }
                nameParts.remove(nameParts.size() - 1);
                path.remove(path.size() - 1);
                continue;
            }
            if (Entity.class.isAssignableFrom(field.type()) || Ref.class.isAssignableFrom(field.type())) {
                // Entity and Ref components without @FK are not write-set edges.
                continue;
            }
            // Recurse into inline records (including composite primary keys), which may carry FK components.
            var nested = REFLECTION.findRecordType(field.type());
            if (nested.isPresent()) {
                path.add(i);
                nameParts.add(field.name());
                collectFkEdges(nested.get(), path, nameParts, fieldInsertable, edges);
                nameParts.remove(nameParts.size() - 1);
                path.remove(path.size() - 1);
            }
        }
    }

    private static int[] toArray(@Nonnull List<Integer> path) {
        int[] result = new int[path.size()];
        for (int i = 0; i < path.size(); i++) {
            result[i] = path.get(i);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Data> refTargetType(@Nonnull RecordField field) {
        if (field.genericType() instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> targetType
                && Data.class.isAssignableFrom(targetType)) {
            return (Class<? extends Data>) targetType;
        }
        throw new PersistenceException("Cannot determine the target type of Ref component '%s.%s'; found '%s'."
                .formatted(field.declaringType().getSimpleName(), field.name(), field.genericType()));
    }
}
