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

/**
 * Typed callback interface for entity lifecycle events.
 *
 * <p>The type parameter {@code E} determines which entity type this callback applies to. The framework automatically
 * resolves the type parameter at runtime and only invokes the callback for matching entity types. Use
 * {@code EntityCallback<Entity<?>>} to create a global callback that fires for all entities.</p>
 *
 * <p>The "before" callbacks for insert, update, and upsert return the (potentially transformed) entity to persist,
 * which is essential for immutable record-based entities that cannot be mutated in place. The "after" callbacks and
 * "before delete" are observers that do not affect the persisted data.</p>
 *
 * <h2>Upsert callback routing</h2>
 *
 * <p>An upsert operation may be executed as a SQL-level upsert (e.g., {@code INSERT ... ON CONFLICT},
 * {@code MERGE}), or it may be routed to a plain insert or update depending on the entity's primary key state and
 * the database dialect. The callbacks that fire depend on which path is taken:</p>
 * <ul>
 *   <li>When routed to <b>insert</b>: {@link #beforeInsert}/{@link #afterInsert} fire.</li>
 *   <li>When routed to <b>update</b>: {@link #beforeUpdate}/{@link #afterUpdate} fire.</li>
 *   <li>When executed as a <b>SQL-level upsert</b>: {@link #beforeUpsert}/{@link #afterUpsert} fire.</li>
 * </ul>
 * <p>Exactly one pair of callbacks fires per entity; they are never combined.</p>
 *
 * <h2>"After" callback entity state</h2>
 *
 * <p>The "after" callbacks always receive the entity as it was sent to the database (after the corresponding "before"
 * transformation), not the entity as it exists in the database after the operation. Database-generated values such
 * as auto-incremented primary keys, version increments, or default column values are not reflected. This applies to
 * all repository methods, including the {@code *AndFetch} variants; the fetched entity is only returned to the
 * caller, not passed to the callback.</p>
 *
 * <p>All methods have default no-op implementations, so users only need to override the hooks they care about.</p>
 *
 * <p>Typical use cases include auditing (setting created/updated timestamps), validation, and logging.</p>
 *
 * @param <E> the entity type this callback applies to. Use {@code Entity<?>} to match all entity types.
 * @since 1.9
 */
public interface EntityCallback<E extends Entity<?>> {

    /**
     * Called before an entity is inserted into the database.
     *
     * <p>The returned entity is the one that will actually be persisted. Implementations may return a modified copy
     * of the entity (e.g., with audit fields populated) or the original entity unchanged.</p>
     *
     * <p>This callback also fires when an upsert operation is routed to an insert (e.g., for auto-generated
     * primary keys on databases that cannot perform a SQL-level upsert with generated keys).</p>
     *
     * @param entity the entity about to be inserted; never {@code null}.
     * @return the entity to insert; never {@code null}.
     */
    default E beforeInsert(@Nonnull E entity) {
        return entity;
    }

    /**
     * Called before an entity is updated in the database.
     *
     * <p>The returned entity is the one that will actually be persisted. Implementations may return a modified copy
     * of the entity (e.g., with an updated timestamp) or the original entity unchanged.</p>
     *
     * <p>This callback also fires when an upsert operation is routed to an update (i.e., when the entity has an
     * auto-generated primary key with a non-default value, indicating it was previously inserted).</p>
     *
     * @param entity the entity about to be updated; never {@code null}.
     * @return the entity to update; never {@code null}.
     */
    default E beforeUpdate(@Nonnull E entity) {
        return entity;
    }

    /**
     * Called after an entity has been successfully inserted into the database.
     *
     * <p>The entity passed to this method is the entity as it was sent to the database (after
     * {@link #beforeInsert} transformation). Database-generated values such as auto-incremented primary keys
     * or default column values are not reflected.</p>
     *
     * <p>This callback also fires when an upsert operation is routed to an insert.</p>
     *
     * @param entity the entity that was inserted; never {@code null}.
     */
    default void afterInsert(@Nonnull E entity) {}

    /**
     * Called after an entity has been successfully updated in the database.
     *
     * <p>The entity passed to this method is the entity as it was sent to the database (after
     * {@link #beforeUpdate} transformation). Database-side changes such as version increments or trigger-applied
     * modifications are not reflected.</p>
     *
     * <p>This callback also fires when an upsert operation is routed to an update.</p>
     *
     * @param entity the entity that was updated; never {@code null}.
     */
    default void afterUpdate(@Nonnull E entity) {}

    /**
     * Called before an entity is upserted via a SQL-level upsert statement (e.g., {@code INSERT ... ON CONFLICT},
     * {@code MERGE}).
     *
     * <p>This callback only fires when the upsert is executed as a SQL-level upsert. When the operation is routed
     * to a plain insert or update, {@link #beforeInsert} or {@link #beforeUpdate} fires instead.</p>
     *
     * <p>The returned entity is the one that will actually be persisted. By default, this delegates to
     * {@link #beforeInsert(Entity)}, so that insert callbacks automatically cover the upsert path.
     * Override this method to provide upsert-specific behavior.</p>
     *
     * @param entity the entity about to be upserted; never {@code null}.
     * @return the entity to upsert; never {@code null}.
     */
    default E beforeUpsert(@Nonnull E entity) {
        return beforeInsert(entity);
    }

    /**
     * Called after an entity has been successfully upserted via a SQL-level upsert statement.
     *
     * <p>This callback only fires when the upsert is executed as a SQL-level upsert. When the operation is routed
     * to a plain insert or update, {@link #afterInsert} or {@link #afterUpdate} fires instead.</p>
     *
     * <p>The entity passed to this method is the entity as it was sent to the database (after
     * {@link #beforeUpsert} transformation). Database-generated values are not reflected.</p>
     *
     * <p>By default, this delegates to {@link #afterInsert(Entity)}, so that insert callbacks automatically
     * cover the upsert path. Override this method to provide upsert-specific behavior.</p>
     *
     * @param entity the entity that was upserted; never {@code null}.
     */
    default void afterUpsert(@Nonnull E entity) {
        afterInsert(entity);
    }

    /**
     * Called before an entity is deleted from the database.
     *
     * @param entity the entity about to be deleted; never {@code null}.
     */
    default void beforeDelete(@Nonnull E entity) {}

    /**
     * Called after an entity has been successfully deleted from the database.
     *
     * @param entity the entity that was deleted; never {@code null}.
     */
    default void afterDelete(@Nonnull E entity) {}
}
