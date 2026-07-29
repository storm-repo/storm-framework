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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import st.orm.Entity;
import st.orm.EntityCallback;

/**
 * Dispatches {@link EntityCallback} invocations for a single entity type.
 *
 * <p>The "after" callbacks observe what the calling repository method reports to its caller: the entity as sent for
 * the methods that return nothing, the entity carrying the primary key the database assigned for the
 * {@code *AndFetchId} methods, and the row as read back for the {@code *AndFetch} methods. The fetching methods build
 * on the id-returning ones, so their callbacks are collected at the point where the write completes and fired once
 * the rows are available, keeping the write itself on a single path.</p>
 *
 * <p>Three thread-scoped concerns are handled here. Callbacks never fire recursively, so database work performed
 * inside a callback runs without triggering callbacks of its own. A {@code *AndFetch} call in flight collects rather
 * than fires. And a write whose caller asked for nothing back withholds any key it retrieved along the way, which is
 * what the write set needs when it reads keys only to bind foreign keys on dependent rows.</p>
 *
 * <p>Instances are per repository and immutable; the thread-scoped state is static so it spans the repositories a
 * single operation touches.</p>
 *
 * @param <E> the entity type.
 * @param <ID> the primary key type.
 * @since 1.13
 */
final class CallbackSupport<E extends Entity<ID>, ID> {

    /**
     * Re-entrancy guard that prevents entity callbacks from firing recursively. When a callback performs database
     * operations (e.g., inserting an audit log), those operations must not trigger callbacks again. Static and
     * thread-local so that it applies across all repository instances on the current thread.
     */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Collects the "after" callbacks of an in-flight {@code *AndFetch} call until the rows have been read back. */
    private static final ThreadLocal<List<Deferred>> DEFERRED = new ThreadLocal<>();

    /** Withholds retrieved primary keys from the "after" callbacks. */
    private static final ThreadLocal<Boolean> WITHHOLD_KEYS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Identifies which "after" callback an invocation dispatches to. */
    private enum After { INSERT, UPDATE, UPSERT }

    /**
     * An "after" callback that has been collected rather than fired, holding the entity as sent to the database, the
     * primary key the database assigned if one was retrieved, and the repository that owns the callbacks.
     */
    private record Deferred(@Nonnull CallbackSupport<?, ?> support,
                            @Nonnull Entity<?> entity,
                            @Nullable Object generatedPrimaryKey,
                            @Nonnull After type) {}

    private final List<EntityCallback<E>> callbacks;

    CallbackSupport(@Nonnull List<EntityCallback<?>> callbacks, @Nonnull Class<E> entityType) {
        this.callbacks = resolve(callbacks, entityType);
    }

    /**
     * Runs a write with retrieved primary keys withheld from the "after" callbacks, so they observe the entities as
     * sent.
     *
     * @param write the write to perform.
     * @return the result of the write.
     */
    static <R> R withoutObservedKeys(@Nonnull Supplier<R> write) {
        if (WITHHOLD_KEYS.get()) {
            return write.get();
        }
        WITHHOLD_KEYS.set(Boolean.TRUE);
        try {
            return write.get();
        } finally {
            WITHHOLD_KEYS.set(Boolean.FALSE);
        }
    }

    /** Returns whether callbacks are registered and would fire on the current thread. */
    boolean isActive() {
        return !callbacks.isEmpty() && !ACTIVE.get();
    }

    //
    // "Before" callbacks.
    //

    E beforeInsert(E entity) {
        return transform(entity, EntityCallback::beforeInsert);
    }

    E beforeUpdate(E entity) {
        return transform(entity, EntityCallback::beforeUpdate);
    }

    E beforeUpsert(E entity) {
        return transform(entity, EntityCallback::beforeUpsert);
    }

    void beforeRemove(E entity) {
        observe(entity, EntityCallback::beforeRemove);
    }

    void afterRemove(E entity) {
        observe(entity, EntityCallback::afterRemove);
    }

    /** Applies each callback in registration order, chaining the entity each one returns. */
    private E transform(E entity, @Nonnull Transformer<E> transformer) {
        if (!isActive()) {
            return entity;
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            for (var callback : callbacks) {
                entity = transformer.apply(callback, entity);
            }
            return entity;
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    /** Invokes each callback in registration order without altering the entity. */
    private void observe(E entity, @Nonnull Observer<E> observer) {
        if (!isActive()) {
            return;
        }
        ACTIVE.set(Boolean.TRUE);
        try {
            for (var callback : callbacks) {
                observer.accept(callback, entity);
            }
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    @FunctionalInterface
    private interface Transformer<E extends Entity<?>> {
        E apply(@Nonnull EntityCallback<E> callback, @Nonnull E entity);
    }

    @FunctionalInterface
    private interface Observer<E extends Entity<?>> {
        void accept(@Nonnull EntityCallback<E> callback, @Nonnull E entity);
    }

    //
    // "After" callbacks.
    //

    /** Fires the after-insert callbacks with the entity as sent to the database. */
    void afterInsert(E entity) {
        fire(entity, null, After.INSERT);
    }

    /** Fires the after-insert callbacks with the entity carrying the primary key the database assigned. */
    void afterInsert(E entity, @Nullable ID generatedPrimaryKey) {
        fire(entity, generatedPrimaryKey, After.INSERT);
    }

    /**
     * Fires the after-insert callbacks for a batch, pairing each entity with the primary key the database assigned.
     * The keys are reported in insertion order, which is the contract the batch insert paths already rely on.
     */
    void afterInsert(@Nonnull List<E> entities, @Nonnull List<ID> generatedPrimaryKeys) {
        fireBatch(entities, generatedPrimaryKeys, After.INSERT);
    }

    void afterUpdate(E entity) {
        fire(entity, null, After.UPDATE);
    }

    /** Fires the after-upsert callbacks with the entity as sent to the database. */
    void afterUpsert(E entity) {
        fire(entity, null, After.UPSERT);
    }

    /** Fires the after-upsert callbacks with the entity carrying the primary key the database assigned. */
    void afterUpsert(E entity, @Nullable ID generatedPrimaryKey) {
        fire(entity, generatedPrimaryKey, After.UPSERT);
    }

    /** Fires the after-upsert callbacks for a batch, pairing each entity with its assigned primary key. */
    void afterUpsert(@Nonnull List<E> entities, @Nonnull List<ID> generatedPrimaryKeys) {
        fireBatch(entities, generatedPrimaryKeys, After.UPSERT);
    }

    private void fireBatch(@Nonnull List<E> entities, @Nonnull List<ID> generatedPrimaryKeys, @Nonnull After type) {
        if (!isActive()) {
            return;
        }
        for (int i = 0; i < entities.size(); i++) {
            fire(entities.get(i), i < generatedPrimaryKeys.size() ? generatedPrimaryKeys.get(i) : null, type);
        }
    }

    /** Dispatches an "after" callback, or collects it when a {@code *AndFetch} call is in flight. */
    private void fire(@Nonnull E entity, @Nullable ID generatedPrimaryKey, @Nonnull After type) {
        if (!isActive()) {
            return;
        }
        if (WITHHOLD_KEYS.get()) {
            generatedPrimaryKey = null;
        }
        var deferred = DEFERRED.get();
        if (deferred != null) {
            deferred.add(new Deferred(this, entity, generatedPrimaryKey, type));
            return;
        }
        invoke(withPrimaryKey(entity, generatedPrimaryKey), type);
    }

    /** Invokes the "after" callbacks of the given type in registration order, guarding against re-entrancy. */
    private void invoke(@Nonnull E entity, @Nonnull After type) {
        ACTIVE.set(Boolean.TRUE);
        try {
            for (var callback : callbacks) {
                switch (type) {
                    case INSERT -> callback.afterInsert(entity);
                    case UPDATE -> callback.afterUpdate(entity);
                    case UPSERT -> callback.afterUpsert(entity);
                }
            }
        } finally {
            ACTIVE.set(Boolean.FALSE);
        }
    }

    /**
     * Returns the entity carrying the primary key the database assigned, rebuilding it when that key differs from the
     * one that was sent. Entities whose type declares no {@code @PK} component to carry the key, and entities whose
     * key was not generated, are returned unchanged.
     */
    @SuppressWarnings("unchecked")
    private E withPrimaryKey(@Nonnull E entity, @Nullable ID generatedPrimaryKey) {
        if (generatedPrimaryKey == null || generatedPrimaryKey.equals(entity.id())) {
            return entity;
        }
        int primaryKeyIndex = RecordRebuilder.primaryKeyIndex(entity.getClass());
        if (primaryKeyIndex < 0) {
            return entity;
        }
        return (E) RecordRebuilder.withComponent(entity, primaryKeyIndex, generatedPrimaryKey);
    }

    //
    // Deferral.
    //

    /**
     * Runs a write that reports its result by reading the rows back, firing the "after" callbacks against those rows
     * rather than against the entities that were sent.
     *
     * <p>Each collected callback is matched to one of the reported rows by primary key. A callback whose row is
     * absent from the fetch, which a concurrent delete can cause, falls back to the entity as sent carrying its key,
     * so a write is never observed twice and never goes unobserved.</p>
     *
     * @param write the write, returning the rows it read back.
     * @return the rows the write read back.
     */
    List<E> fetchAndFire(@Nonnull Supplier<List<E>> write) {
        if (!isActive()) {
            return write.get();
        }
        return fetchAndFire(write, rows -> rows);
    }

    /**
     * Runs a write that reports rows read back, firing the "after" callbacks against those rows rather than against
     * the entities that were sent.
     *
     * <p>Static so that a write set, which spans repositories of several types, can wrap its whole execution: each
     * collected callback replays against the callbacks that collected it, and is matched to a reported row by type
     * and primary key. A callback whose row is not among those reported falls back to the entity as sent carrying
     * its key, so a write is never observed twice and never goes unobserved.</p>
     *
     * @param write the write to perform.
     * @param rows the rows the write read back, extracted from its result.
     * @return the result of the write.
     */
    static <R> R fetchAndFire(@Nonnull Supplier<R> write, @Nonnull Function<R, List<? extends Entity<?>>> rows) {
        var previous = DEFERRED.get();
        var deferred = new ArrayList<Deferred>();
        R result;
        DEFERRED.set(deferred);
        try {
            result = write.get();
        } finally {
            if (previous == null) {
                DEFERRED.remove();
            } else {
                DEFERRED.set(previous);
            }
        }
        replay(deferred, rows.apply(result));
        return result;
    }

    /**
     * Returns whether the {@code *AndFetch} call in flight has collected any callback, so a caller can decide how
     * widely to read rows back before those callbacks are fired.
     */
    static boolean hasDeferred() {
        var deferred = DEFERRED.get();
        return deferred != null && !deferred.isEmpty();
    }

    /** Identifies a reported row, so entities of different types that share a primary key stay distinct. */
    private record TypeIdKey(@Nonnull Class<?> type, @Nullable Object id) {}

    private static void replay(@Nonnull List<Deferred> deferred, @Nonnull List<? extends Entity<?>> fetched) {
        if (deferred.isEmpty()) {
            return;
        }
        var byTypeAndKey = new HashMap<TypeIdKey, Entity<?>>();
        for (Entity<?> entity : fetched) {
            byTypeAndKey.put(new TypeIdKey(entity.getClass(), entity.id()), entity);
        }
        for (var entry : deferred) {
            entry.support().replayOne(entry, byTypeAndKey);
        }
    }

    @SuppressWarnings("unchecked")
    private void replayOne(@Nonnull Deferred entry, @Nonnull HashMap<TypeIdKey, Entity<?>> byTypeAndKey) {
        E sent = withPrimaryKey((E) entry.entity(), (ID) entry.generatedPrimaryKey());
        Entity<?> row = byTypeAndKey.get(new TypeIdKey(sent.getClass(), sent.id()));
        invoke(row == null ? sent : (E) row, entry.type());
    }

    //
    // Resolution.
    //

    /**
     * Resolves the entity callbacks that match the given entity type, filtering by the generic type parameter
     * declared on each {@link EntityCallback}.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Entity<ID>, ID> List<EntityCallback<E>> resolve(
            @Nonnull List<EntityCallback<?>> callbacks, @Nonnull Class<E> entityType) {
        var result = new ArrayList<EntityCallback<E>>();
        for (var callback : callbacks) {
            Class<?> callbackType = resolveEntityType(callback.getClass());
            if (callbackType.isAssignableFrom(entityType)) {
                result.add((EntityCallback<E>) callback);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Resolves the entity type parameter {@code E} from a concrete {@link EntityCallback} class by inspecting its
     * generic interface hierarchy.
     */
    private static Class<?> resolveEntityType(@Nonnull Class<?> clazz) {
        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt) {
                if (pt.getRawType() == EntityCallback.class) {
                    return extractClass(pt.getActualTypeArguments()[0]);
                }
                if (pt.getRawType() instanceof Class<?> raw && EntityCallback.class.isAssignableFrom(raw)) {
                    Class<?> resolved = resolveEntityType(raw);
                    if (resolved != Entity.class) {
                        return resolved;
                    }
                }
            } else if (iface instanceof Class<?> raw && EntityCallback.class.isAssignableFrom(raw)) {
                Class<?> resolved = resolveEntityType(raw);
                if (resolved != Entity.class) {
                    return resolved;
                }
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            return resolveEntityType(superclass);
        }
        return Entity.class;
    }

    private static Class<?> extractClass(@Nonnull Type type) {
        if (type instanceof Class<?> cls) {
            return cls;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        return Entity.class;
    }
}
