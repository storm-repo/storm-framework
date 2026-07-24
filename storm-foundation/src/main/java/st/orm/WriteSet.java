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
package st.orm;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Dependency-aware write operations over mixed-type sets of entities.
 *
 * <p>A write set applies one write operation to a heterogeneous collection of entities. {@link #insert(Iterable)}
 * and {@link #upsert(Iterable)} extend the <em>explicit members</em> (the entities supplied by the caller) with
 * <em>discovered members</em>: unsaved entities transitively reachable through insertable, entity-valued foreign key
 * fields. {@link #update(Iterable)} and {@link #remove(Iterable)} operate on the
 * explicit members only. Each action accepts the entities as any {@code Iterable} or as varargs. The per-row
 * semantics of each action are identical to the corresponding {@code EntityRepository} operation; the write set adds
 * partitioning by type, dependency ordering and generated-key propagation:</p>
 *
 * <ul>
 *   <li><strong>Insert discovery.</strong> A record whose foreign key field holds an unsaved entity is a value that
 *       describes both rows; inserting the value inserts both. Discovery traverses entity-valued foreign key fields
 *       (including fields inside inline components) and entity-wrapped refs (see {@link Ref#of(Entity)}). Referenced
 *       entities that already carry a primary key are never discovered; unless they are explicit members themselves,
 *       they only provide foreign key values.</li>
 *   <li><strong>Ordering.</strong> A valid execution order is determined from the foreign key dependencies:
 *       parents-before-children for {@link #insert(Iterable)} and {@link #upsert(Iterable)},
 *       children-before-parents for {@link #remove(Iterable)}. {@link #update(Iterable)} has no ordering
 *       constraints and is only grouped by type.</li>
 *   <li><strong>Key propagation.</strong> Generated primary keys propagate within the set by <em>instance
 *       identity</em>: a child links to its new parent by holding the same instance, either directly in the foreign
 *       key field or wrapped in a {@code Ref}. The same unsaved instance describes one prospective row; two
 *       structurally equal but distinct unsaved instances describe two rows. When a foreign key field is
 *       non-insertable because its column value is carried by a component of the primary key (the junction table
 *       pattern, where the key columns live inside a composite primary key), the generated key is written into the
 *       carrying key component instead.</li>
 *   <li><strong>Batching.</strong> Execution is grouped into one batch operation per entity type per dependency
 *       level (large batches are split by the configured batch size). The number of batches follows the dependency
 *       shape of the data: the Owner &larr; Pet &larr; Visit example below needs three, and a self-referencing type
 *       whose rows span several dependency levels needs one batch per level.</li>
 * </ul>
 *
 * <p>An entity is considered <em>unsaved</em> when its primary key is the default value and the primary key is
 * auto-generated (identity or sequence). This test is local and deterministic; no session state, entity cache or
 * database round trip is involved. There is no session-wide cascade or persistence context: all writes derive from
 * the entities supplied to the call and, for insert and upsert, their discovered members.</p>
 *
 * <p>A write set executes multiple statements and is not atomic by itself: when a later dependency level fails, the
 * earlier levels have already been written. Run write sets inside a transaction when atomicity across the set is
 * required.</p>
 *
 * <p>Example, inserting a three-level graph with a shared new parent:</p>
 *
 * <pre>{@code
 * var owner = new Owner("Alice", address);                    // unsaved
 * var wolfie = new Pet("Wolfie", DOG, owner);                 // both pets share the owner instance
 * var rex = new Pet("Rex", DOG, owner);
 * var visit = new Visit(TODAY, "Check-up", wolfie);
 * orm.writeSet().insert(wolfie, rex, visit);                  // owner joins via insert discovery:
 *                                                             // one Owner, one Pet and one Visit batch
 * }</pre>
 *
 * <p>Unsaved references that cannot be discovered fail fast with a descriptive exception before
 * anything is written: an id-only {@code Ref} carrying a default id, an unsaved entity behind a non-insertable
 * foreign key component whose column value is not carried by an insertable primary key component, an unsaved
 * entity encountered by {@link #update(Iterable)} or {@link #remove(Iterable)}, and dependency cycles that cannot
 * be executed by the dependency-ordering strategy (the write set does not break cycles using nullable intermediate
 * values, deferred constraints or follow-up updates).</p>
 *
 * <p><strong>Note on modified referenced entities:</strong> a keyed entity held in a foreign key field contributes
 * exactly its primary key; modifications to it are not persisted by writing its dependent, by any action. One rule
 * covers every action: a write set writes the entities named by the caller, plus the entities the values make
 * necessary. An unsaved referenced entity is necessary (its dependent cannot be written without its key, and a row
 * that does not exist cannot be a stale copy); a keyed referenced entity never is: it is the state that was hydrated
 * when the value was read, and treating that snapshot as write intent would silently overwrite newer database state.
 * To persist changes to a referenced entity, pass it as an explicit member.</p>
 *
 * <p><strong>Note on unsaved refs:</strong> {@code Ref} equality is based on type and id. Two refs wrapping distinct
 * unsaved instances therefore compare equal until the instances are persisted. Do not use unsaved refs as map keys or
 * set members; the write set itself correlates by instance identity and is not affected.</p>
 *
 * <p><strong>Note on entity callbacks:</strong> callbacks run inside the per-type repository operations, after the
 * write set has discovered members and planned the execution order. A callback that alters foreign key fields does
 * not change which entities are discovered or in which order they are written.</p>
 *
 * @see Ref#of(Entity)
 * @since 1.13
 */
public interface WriteSet {

    /**
     * Inserts the explicit members and their discovered members, in dependency order.
     *
     * <p>All explicit members are inserted with the exact semantics of the per-repository insert: auto-generated
     * primary keys are assigned by the database (a preset value on an auto-generated key is ignored), and entities
     * with non-generated keys are inserted with the key they carry. Unsaved entities reachable through insertable
     * foreign key fields join the set as discovered members and are inserted before their dependents, with generated
     * keys propagated by instance identity.</p>
     *
     * @param entities the entities to insert; may span multiple entity types.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the insert fails.
     */
    void insert(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Inserts the given entities and their discovered members; see {@link #insert(Iterable)}. An empty call is a
     * no-op.
     *
     * @param entities the entities to insert; may span multiple entity types.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the insert fails.
     */
    default void insert(@Nonnull Entity<?>... entities) {
        insert(List.of(entities));
    }

    /**
     * Inserts like {@link #insert(Iterable)} and returns the explicit members as they exist in the database after
     * the insert, in input order.
     *
     * <p>The returned entities are re-fetched, so database-applied changes such as generated keys, defaults and
     * version columns are reflected, and discovered members referenced by them are hydrated with their generated
     * keys.</p>
     *
     * @param entities the entities to insert; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if the insert fails.
     */
    @Nonnull
    List<Entity<?>> insertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Inserts like {@link #insertAndFetch(Iterable)} and returns the explicit members as they exist in the database
     * after the insert, in input order; an empty call is a no-op and returns an empty list.
     *
     * @param entities the entities to insert; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if the insert fails.
     */
    @Nonnull
    default List<Entity<?>> insertAndFetch(@Nonnull Entity<?>... entities) {
        return insertAndFetch(List.of(entities));
    }

    /**
     * Inserts like {@link #insert(Iterable)} and returns the primary keys of the explicit members, in input order.
     *
     * <p>The keys are taken from the insert itself: generated keys as reported by the database, or the keys the
     * entities carry when the primary key is not generated. The rows are not re-read, so database-applied defaults
     * and version columns are not reflected; use {@link #insertAndFetch(Iterable)} when that state is needed.
     * Discovered members are inserted but not reported.</p>
     *
     * <p>The batch is homogeneous in its id type; entity types may differ as long as they share it. For batches
     * that mix id types, use {@link #insertAndFetch(Iterable)}, where each returned entity carries its own id.</p>
     *
     * @param entities the entities to insert; may span multiple entity types sharing the id type.
     * @return the primary keys of the explicit members in input order.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the insert fails.
     */
    @Nonnull
    <ID> List<ID> insertAndFetchIds(@Nonnull Iterable<? extends Entity<ID>> entities);

    /**
     * Inserts the given entity and its discovered members and returns its primary key; see
     * {@link #insertAndFetchIds(Iterable)}.
     *
     * @param entity the entity to insert.
     * @return the primary key of the inserted entity.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the insert fails.
     */
    @Nonnull
    default <ID> ID insertAndFetchId(@Nonnull Entity<ID> entity) {
        return insertAndFetchIds(List.of(entity)).getFirst();
    }

    /**
     * Updates the given entities, grouped by type.
     *
     * <p>Per-row semantics are identical to the per-repository update, including transaction-scoped dirty checking:
     * entities that are unchanged compared to their observed state are skipped. Only the explicit members are
     * updated; referenced entities are never updated implicitly, and there is no insert discovery &mdash; an
     * unsaved explicit member is rejected (a row that does not exist cannot be updated), and an unsaved referenced
     * entity fails where its key is required as a foreign key value.</p>
     *
     * <p>In particular, a modified referenced entity is not written: a keyed entity held in a foreign key field of a
     * member contributes only its primary key, so its changes stay in memory. To persist changes to both a member and
     * an entity it references, pass both as explicit members; dirty checking skips whichever members are
     * unchanged.</p>
     *
     * @param entities the entities to update; may span multiple entity types.
     * @throws PersistenceException if an explicit member or a referenced entity is unsaved, or if the update fails.
     */
    void update(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Updates the given entities; see {@link #update(Iterable)}. An empty call is a no-op.
     *
     * @param entities the entities to update; may span multiple entity types.
     * @throws PersistenceException if an explicit member or a referenced entity is unsaved, or if the update fails.
     */
    default void update(@Nonnull Entity<?>... entities) {
        update(List.of(entities));
    }

    /**
     * Updates like {@link #update(Iterable)} and returns the passed entities as they exist in the database after the
     * update, in input order.
     *
     * @param entities the entities to update; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if an explicit member or a referenced entity is unsaved, or if the update fails.
     */
    @Nonnull
    List<Entity<?>> updateAndFetch(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Updates like {@link #updateAndFetch(Iterable)} and returns the passed entities as they exist in the database
     * after the update, in input order; an empty call is a no-op and returns an empty list.
     *
     * @param entities the entities to update; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if an explicit member or a referenced entity is unsaved, or if the update fails.
     */
    @Nonnull
    default List<Entity<?>> updateAndFetch(@Nonnull Entity<?>... entities) {
        return updateAndFetch(List.of(entities));
    }

    /**
     * Upserts the explicit members and inserts their discovered members, in dependency order.
     *
     * <p>Explicit members are upserted with the exact semantics of the per-repository upsert (native
     * {@code ON CONFLICT} / {@code MERGE} where available); explicit membership takes precedence, so a keyed entity
     * that is both supplied and referenced by another member is upserted, and is written before its dependents.
     * Unsaved entities reachable through insertable foreign key fields join the set as discovered members and are
     * <em>inserted</em> before their dependents, with generated keys propagated by instance identity. Keyed
     * referenced entities that are not explicit members only provide foreign key values; modifications to them are
     * not persisted.</p>
     *
     * @param entities the entities to upsert; may span multiple entity types.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the upsert fails.
     */
    void upsert(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Upserts the given entities and inserts their discovered members; see {@link #upsert(Iterable)}. An empty call
     * is a no-op.
     *
     * @param entities the entities to upsert; may span multiple entity types.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the upsert fails.
     */
    default void upsert(@Nonnull Entity<?>... entities) {
        upsert(List.of(entities));
    }

    /**
     * Upserts like {@link #upsert(Iterable)} and returns the passed entities as they exist in the database after the
     * upsert, in input order.
     *
     * @param entities the entities to upsert; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if the upsert fails.
     */
    @Nonnull
    List<Entity<?>> upsertAndFetch(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Upserts like {@link #upsertAndFetch(Iterable)} and returns the passed entities as they exist in the database
     * after the upsert, in input order; an empty call is a no-op and returns an empty list.
     *
     * @param entities the entities to upsert; may span multiple entity types.
     * @return the fetched entities in input order.
     * @throws PersistenceException if the upsert fails.
     */
    @Nonnull
    default List<Entity<?>> upsertAndFetch(@Nonnull Entity<?>... entities) {
        return upsertAndFetch(List.of(entities));
    }

    /**
     * Upserts like {@link #upsert(Iterable)} and returns the primary keys of the explicit members, in input order.
     *
     * <p>For inserted rows the generated key is reported; for updated rows the key the entity carries. The rows are
     * not re-read, so database-applied defaults and version columns are not reflected; use
     * {@link #upsertAndFetch(Iterable)} when that state is needed. Discovered members are written but not
     * reported.</p>
     *
     * <p>The batch is homogeneous in its id type; entity types may differ as long as they share it. For batches
     * that mix id types, use {@link #upsertAndFetch(Iterable)}, where each returned entity carries its own id.</p>
     *
     * @param entities the entities to upsert; may span multiple entity types sharing the id type.
     * @return the primary keys of the explicit members in input order.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the upsert fails.
     */
    @Nonnull
    <ID> List<ID> upsertAndFetchIds(@Nonnull Iterable<? extends Entity<ID>> entities);

    /**
     * Upserts the given entity and its discovered members and returns its primary key; see
     * {@link #upsertAndFetchIds(Iterable)}.
     *
     * @param entity the entity to upsert.
     * @return the primary key of the upserted entity.
     * @throws PersistenceException if the dependencies contain a cycle that cannot be ordered, if an unsaved entity
     * is referenced through a non-insertable foreign key component, or if the upsert fails.
     */
    @Nonnull
    default <ID> ID upsertAndFetchId(@Nonnull Entity<ID> entity) {
        return upsertAndFetchIds(List.of(entity)).getFirst();
    }

    /**
     * Removes the given entities, children before parents.
     *
     * <p>Only the explicit members are removed; referenced entities are never removed implicitly. Dependencies
     * between set members are resolved by entity type and primary key (not instance identity), so a member
     * referencing another member through a foreign key is removed first, regardless of whether the two hold the same
     * instance. Unsaved entities are rejected.</p>
     *
     * @param entities the entities to remove; may span multiple entity types.
     * @throws PersistenceException if a passed entity is unsaved, or if the removal fails.
     */
    void remove(@Nonnull Iterable<? extends Entity<?>> entities);

    /**
     * Removes the given entities, children before parents; see {@link #remove(Iterable)}. An empty call is a no-op.
     *
     * @param entities the entities to remove; may span multiple entity types.
     * @throws PersistenceException if a passed entity is unsaved, or if the removal fails.
     */
    default void remove(@Nonnull Entity<?>... entities) {
        remove(List.of(entities));
    }

    //
    // Single-root convenience variants.
    //

    /**
     * Inserts the given entity and its discovered members; see {@link #insert(Iterable)}.
     *
     * @param entity the root entity to insert.
     * @throws PersistenceException if the insert fails.
     */
    default void insert(@Nonnull Entity<?> entity) {
        insert(List.of(entity));
    }

    /**
     * Inserts the given entity and its discovered members, and returns the entity as it exists in the database after
     * the insert; see {@link #insertAndFetch(Iterable)}.
     *
     * @param entity the root entity to insert.
     * @param <E> the entity type.
     * @return the fetched entity, with generated keys, defaults and version columns reflected and discovered members
     * hydrated.
     * @throws PersistenceException if the insert fails.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    default <E extends Entity<?>> E insertAndFetch(@Nonnull E entity) {
        return (E) insertAndFetch(List.of(entity)).getFirst();
    }

    /**
     * Updates the given entity; see {@link #update(Iterable)}.
     *
     * @param entity the entity to update.
     * @throws PersistenceException if the entity is unsaved or the update fails.
     */
    default void update(@Nonnull Entity<?> entity) {
        update(List.of(entity));
    }

    /**
     * Updates the given entity and returns it as it exists in the database after the update; see
     * {@link #updateAndFetch(Iterable)}.
     *
     * @param entity the entity to update.
     * @param <E> the entity type.
     * @return the fetched entity.
     * @throws PersistenceException if the entity is unsaved or the update fails.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    default <E extends Entity<?>> E updateAndFetch(@Nonnull E entity) {
        return (E) updateAndFetch(List.of(entity)).getFirst();
    }

    /**
     * Upserts the given entity and inserts its discovered members; see {@link #upsert(Iterable)}.
     *
     * @param entity the root entity to upsert.
     * @throws PersistenceException if the upsert fails.
     */
    default void upsert(@Nonnull Entity<?> entity) {
        upsert(List.of(entity));
    }

    /**
     * Upserts the given entity, inserts its discovered members, and returns the entity as it exists in the database
     * after the upsert; see {@link #upsertAndFetch(Iterable)}.
     *
     * @param entity the root entity to upsert.
     * @param <E> the entity type.
     * @return the fetched entity.
     * @throws PersistenceException if the upsert fails.
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    default <E extends Entity<?>> E upsertAndFetch(@Nonnull E entity) {
        return (E) upsertAndFetch(List.of(entity)).getFirst();
    }

    /**
     * Removes the given entity; see {@link #remove(Iterable)}.
     *
     * @param entity the entity to remove.
     * @throws PersistenceException if the entity is unsaved or the removal fails.
     */
    default void remove(@Nonnull Entity<?> entity) {
        remove(List.of(entity));
    }
}
