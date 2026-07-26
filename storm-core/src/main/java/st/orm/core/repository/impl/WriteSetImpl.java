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
import st.orm.core.spi.Instantiators;
import st.orm.core.spi.ORMReflection;
import st.orm.core.spi.Providers;
import st.orm.core.spi.RowIdentity;
import st.orm.core.template.Column;
import st.orm.core.template.Model;
import st.orm.mapping.Instantiator;
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
    private enum Action { INSERT, UPSERT, UPDATE, REMOVE }

    private final RepositoryLookup lookup;
    private final ConcurrentMap<Class<?>, TypeInfo> typeInfoCache = new ConcurrentHashMap<>();

    public WriteSetImpl(@Nonnull RepositoryLookup lookup) {
        this.lookup = requireNonNull(lookup, "lookup");
    }

    @Override
    public void insert(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeOrdered(entities, Action.INSERT, false);
    }

    @Override
    @Nonnull
    public List<Entity<?>> insertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities) {
        Execution execution = executeOrdered(entities, Action.INSERT, true);
        return fetch(execution);
    }

    @Override
    @Nonnull
    public <ID> List<ID> insertAndFetchIds(@Nonnull Iterable<? extends Entity<ID>> entities) {
        Execution execution = executeOrdered(entities, Action.INSERT, true);
        return fetchIds(execution);
    }

    @Override
    public void upsert(@Nonnull Iterable<? extends Entity<?>> entities) {
        executeOrdered(entities, Action.UPSERT, false);
    }

    @Override
    @Nonnull
    public List<Entity<?>> upsertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities) {
        Execution execution = executeOrdered(entities, Action.UPSERT, true);
        return fetch(execution);
    }

    @Override
    @Nonnull
    public <ID> List<ID> upsertAndFetchIds(@Nonnull Iterable<? extends Entity<ID>> entities) {
        Execution execution = executeOrdered(entities, Action.UPSERT, true);
        return fetchIds(execution);
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

    private Execution executeOrdered(@Nonnull Iterable<? extends Entity<?>> entities, @Nonnull Action action,
                                     boolean fetchKeys) {
        var inputs = new ArrayList<>();
        entities.forEach(inputs::add);
        // Discover the members to write: explicit members plus unsaved entities transitively reachable through
        // insertable foreign key fields.
        var nodes = new IdentityHashMap<Object, Node>();
        var discoveryOrder = new ArrayList<Node>();
        var queue = new ArrayDeque<Node>();
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
                Object target = resolveTarget(node.entity, edge, action);
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
        linkOrderingDependencies(discoveryOrder, action);
        assignLevels(nodes, discoveryOrder);
        // A node's generated key is only fetched when something consumes it: a dependent node (key propagation) or
        // the caller (AndFetch). Everything else is written without fetch mode, keeping the write compatible with
        // dialects that cannot return generated keys for every generation strategy.
        var keyConsumers = new IdentityHashMap<Object, Boolean>();
        for (Node node : discoveryOrder) {
            for (Dependency dependency : node.dependencies) {
                keyConsumers.put(dependency.target(), Boolean.TRUE);
            }
        }
        // Execute level by level, batched per type. Discovered members are always inserted; explicit members are
        // inserted or upserted according to the action.
        var persistedView = new IdentityHashMap<>();
        for (var byType : groupByLevelAndType(discoveryOrder)) {
            for (var entry : byType.entrySet()) {
                TypeInfo info = typeInfo(entry.getKey());
                if (action == Action.UPSERT) {
                    // Discovered members are inserted; only explicit members carry upsert semantics.
                    persistBatch(info, entry.getValue().stream().filter(node -> !node.passed).toList(),
                            persistedView, Action.INSERT, fetchKeys, keyConsumers);
                    persistBatch(info, entry.getValue().stream().filter(node -> node.passed).toList(),
                            persistedView, Action.UPSERT, fetchKeys, keyConsumers);
                } else {
                    persistBatch(info, entry.getValue(), persistedView, Action.INSERT, fetchKeys, keyConsumers);
                }
            }
        }
        return new Execution(inputs, persistedView);
    }

    /**
     * Records ordering-only dependencies between set members correlated by primary key. A member with a preserved
     * primary key may be referenced by another member through its key rather than by instance; such a reference
     * carries no key propagation, but the referenced row must be written first to satisfy foreign key constraints.
     * A key is preserved when it is not generated, or when the member receives upsert semantics: an upsert matches
     * on the provided key instead of generating a new one, so an explicitly passed keyed member of an upsert set is
     * orderable even when its key column is auto-generated. A member whose key propagation still writes into its
     * primary key (a junction row awaiting a parent's generated key) carries a transient key and is not registered.
     */
    private void linkOrderingDependencies(@Nonnull List<Node> discoveryOrder, @Nonnull Action action) {
        var keyedMembers = new HashMap<TypeIdKey, Node>();
        for (Node node : discoveryOrder) {
            TypeInfo info = typeInfo(node.entity.getClass());
            boolean keyPreserved = !info.autoGeneratedPrimaryKey || (action == Action.UPSERT && node.passed);
            if (keyPreserved && node.dependencies.stream().noneMatch(dependency ->
                    writesPrimaryKey(info, dependency.edge()))) {
                Object id = ((Entity<?>) node.entity).id();
                if (!REFLECTION.isDefaultValue(id)) {
                    keyedMembers.put(new TypeIdKey(node.entity.getClass(), id), node);
                }
            }
        }
        if (keyedMembers.isEmpty()) {
            return;
        }
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

    /** Groups the nodes into per-level maps of type to nodes, preserving discovery order within each group. */
    private static List<Map<Class<?>, List<Node>>> groupByLevelAndType(@Nonnull List<Node> nodes) {
        int maxLevel = nodes.stream().mapToInt(node -> node.level).max().orElse(-1);
        var levels = new ArrayList<Map<Class<?>, List<Node>>>(maxLevel + 1);
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
                              @Nonnull Action action,
                              boolean fetchKeys,
                              @Nonnull IdentityHashMap<Object, Boolean> keyConsumers) {
        if (nodes.isEmpty()) {
            return;
        }
        var prepared = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            prepared.add(propagateKeys(node, persistedView));
        }
        EntityRepository repository = info.repository;
        boolean keysNeeded = info.autoGeneratedPrimaryKey && nodes.stream().anyMatch(
                node -> keyConsumers.containsKey(node.entity) || (fetchKeys && node.passed));
        if (keysNeeded) {
            // The fetch-ids operations return the ids in input order; the same positional contract is relied upon
            // by the repository implementations themselves (see JoinedEntityHelper).
            List<?> ids = action == Action.UPSERT
                    ? repository.upsertAndFetchIds(prepared)
                    : repository.insertAndFetchIds(prepared);
            for (int i = 0; i < nodes.size(); i++) {
                persistedView.put(nodes.get(i).entity, withPrimaryKey(info, prepared.get(i), ids.get(i)));
            }
        } else {
            if (action == Action.UPSERT) {
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
        var inputs = new ArrayList<>();
        var byType = new LinkedHashMap<Class<?>, List<Object>>();
        for (var entity : entities) {
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
        var persistedView = new IdentityHashMap<>();
        for (Object input : inputs) {
            persistedView.put(input, input);
        }
        return new Execution(inputs, persistedView);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void update(@Nonnull EntityRepository repository, @Nonnull List<Object> batch) {
        repository.update(batch);
    }

    //
    // Remove.
    //

    private void executeRemove(@Nonnull Iterable<? extends Entity<?>> entities) {
        // Members are correlated by primary key rather than instance identity: two instances describing the same row
        // are removed once, and dependencies hold regardless of which instance a member embeds.
        var members = new LinkedHashMap<TypeIdKey, Object>();
        for (var entity : entities) {
            if (isUnsaved(entity)) {
                throw new PersistenceException(("Cannot remove unsaved %s. Its primary key is the default value, " +
                        "so it does not describe a database row.").formatted(entity.getClass().getSimpleName()));
            }
            members.putIfAbsent(new TypeIdKey(entity.getClass(), entity.id()), entity);
        }
        // Build member-to-member dependencies via FK values; children must be removed before their parents.
        var nodes = new IdentityHashMap<Object, Node>();
        var order = new ArrayList<Node>();
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
        repository.remove(batch);
    }

    /**
     * Key correlating members by database row rather than by instance. The id is normalized through
     * {@link RowIdentity}: comparing raw ids would make row identity depend on every non-key column of an
     * entity-typed key, so two representations of the same row would fail to correlate whenever such a column does
     * not round-trip bit-exact (a second-precision timestamp column, a database-managed column, a numeric scale
     * difference).
     */
    private record TypeIdKey(Class<?> type, Object id) {
        TypeIdKey {
            id = RowIdentity.normalize(id);
        }
    }

    //
    // Shared machinery.
    //

    /**
     * Assigns each node the length of its longest dependency chain, so that a node always lands on a higher level
     * than everything it depends on. Fails fast when a dependency cycle prevents an ordering.
     */
    static void assignLevels(@Nonnull IdentityHashMap<Object, Node> nodes, @Nonnull List<Node> order) {
        var remaining = new ArrayList<>(order);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            var next = new ArrayList<Node>();
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
     * or the reference is keyed by id only. Id-only refs carrying a default id cannot be discovered and fail fast
     * for insert and upsert.
     */
    @Nullable
    private Object resolveTarget(@Nonnull Object entity, @Nonnull FkEdge edge, @Nonnull Action action) {
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
            if ((action == Action.INSERT || action == Action.UPSERT) && REFLECTION.isDefaultValue(ref.id())) {
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
     * Rebuild metadata per record class: the record type with its canonical constructor made accessible once, and
     * the generated metamodel instantiator when one is registered, so rebuilds construct through generated code
     * rather than reflection.
     */
    private record RebuildType(@Nonnull RecordType recordType, @Nullable Instantiator<?> instantiator) {
        Object newInstance(@Nonnull Object[] args) {
            return instantiator != null ? instantiator.instantiate(args) : recordType.newInstance(args);
        }

        /**
         * Reads the record's component values in declaration order, through the generated deconstructor when the
         * metamodel registered one, so rebuilds run as generated code on both sides of the round trip.
         */
        @SuppressWarnings("unchecked")
        Object[] deconstruct(@Nonnull Object record) {
            if (instantiator != null) {
                Object[] args = ((Instantiator<Object>) instantiator).deconstruct(record);
                if (args != null) {
                    return args;
                }
            }
            List<RecordField> fields = recordType.fields();
            Object[] args = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                args[i] = REFLECTION.invoke(fields.get(i), record);
            }
            return args;
        }
    }

    private static final ClassValue<RebuildType> REBUILD_TYPES = new ClassValue<>() {
        @Override
        protected RebuildType computeValue(@Nonnull Class<?> type) {
            RecordType recordType = REFLECTION.getRecordType(type);
            recordType.constructor().trySetAccessible();
            return new RebuildType(recordType, Instantiators.find(type));
        }
    };

    /**
     * Rebuilds the record with the component at the given path replaced, reconstructing nested records as needed.
     *
     * <p>Intermediate components along the path are never {@code null} here: a dependency is only recorded when
     * {@link #valueAt(Object, int[])} resolved a non-null target through the same path, and records are
     * immutable.</p>
     */
    private Object withComponent(@Nonnull Object record, @Nonnull int[] path, int depth, @Nullable Object newValue) {
        RebuildType rebuildType = REBUILD_TYPES.get(record.getClass());
        Object[] args = rebuildType.deconstruct(record);
        int index = path[depth];
        args[index] = depth == path.length - 1
                ? newValue
                : withComponent(args[index], path, depth + 1, newValue);
        return rebuildType.newInstance(args);
    }

    /** Rebuilds the entity with the given primary key. */
    private Object withPrimaryKey(@Nonnull TypeInfo info, @Nonnull Object entity, @Nonnull Object pk) {
        return withComponent(entity, new int[] {info.primaryKeyIndex}, 0, pk);
    }

    //
    // Fetch support.
    //

    /** Reports the primary keys of the passed entities from the persisted view, in input order. */
    @SuppressWarnings("unchecked")
    @Nonnull
    private <ID> List<ID> fetchIds(@Nonnull Execution execution) {
        var result = new ArrayList<ID>(execution.inputs().size());
        for (Object input : execution.inputs()) {
            Object persisted = requireNonNull(execution.persistedView().get(input), "persisted view");
            result.add((ID) ((Entity<?>) persisted).id());
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    private List<Entity<?>> fetch(@Nonnull Execution execution) {
        // Collect the ids of the passed entities per type, preserving first-seen type order.
        var idsByType = new LinkedHashMap<Class<?>, List<Object>>();
        for (Object input : execution.inputs()) {
            Object persisted = execution.persistedView().get(input);
            Object id = ((Entity<?>) requireNonNull(persisted, "persisted view")).id();
            idsByType.computeIfAbsent(input.getClass(), type -> new ArrayList<>()).add(id);
        }
        var fetched = new HashMap<TypeIdKey, Entity<?>>();
        for (var entry : idsByType.entrySet()) {
            EntityRepository repository = typeInfo(entry.getKey()).repository;
            for (Object entity : (List<Object>) repository.findAllById(entry.getValue())) {
                Entity<?> fetchedEntity = (Entity<?>) entity;
                fetched.put(new TypeIdKey(entry.getKey(), fetchedEntity.id()), fetchedEntity);
            }
        }
        var result = new ArrayList<Entity<?>>(execution.inputs().size());
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
            var edges = new ArrayList<FkEdge>();
            collectFkEdges(recordType, new ArrayList<>(), new ArrayList<>(), true, edges);
            if (edges.stream().anyMatch(edge -> !edge.insertable)) {
                resolveKeyCarriers(recordType, edges);
            }
            this.fkEdges = List.copyOf(edges);
        }

        /**
         * Resolves, for each non-insertable FK component, the insertable component that carries its column value:
         * the primary key field whose column shares the FK component's column name, as in a junction table whose
         * key columns live inside a composite primary key. Components with a carrier can join insert
         * discovery; the generated key is propagated into the carrier instead of through the component itself.
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
            var carrierByColumnName = new HashMap<String, int[]>();
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
            var foreignKeyColumnsByFieldPath = new HashMap<String, List<Column>>();
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
        var leaves = new ArrayList<int[]>();
        var path = new ArrayList<Integer>();
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
            path.removeLast();
            if (!scalar) {
                return false;
            }
        }
        return true;
    }

    /** Returns the record field at the given component path, descending through nested records. */
    private static RecordField fieldAt(@Nonnull RecordType rootType, @Nonnull int[] path) {
        RecordField field = rootType.fields().get(path[0]);
        for (int depth = 1; depth < path.length; depth++) {
            RecordField parent = field;
            // Every path element but the last addresses a nested record; a non-record here means the path is malformed.
            RecordType nested = REFLECTION.findRecordType(parent.type()).orElseThrow(() ->
                    new PersistenceException("Cannot resolve component path: '%s' is not a record.".formatted(parent.name())));
            field = nested.fields().get(path[depth]);
        }
        return field;
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
                    //noinspection unchecked
                    edges.add(new FkEdge(toArray(path), field.name(), fieldPath, false,
                            (Class<? extends Data>) field.type(), fieldInsertable, null));
                }
                nameParts.removeLast();
                path.removeLast();
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
                nameParts.removeLast();
                path.removeLast();
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
