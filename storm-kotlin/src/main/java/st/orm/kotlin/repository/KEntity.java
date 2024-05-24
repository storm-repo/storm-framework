/*
 * Copyright 2024 the original author or authors.
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
package st.orm.kotlin.repository;

import jakarta.annotation.Nonnull;
import jakarta.persistence.PersistenceException;
import st.orm.Inline;
import st.orm.PK;
import st.orm.repository.Entity;
import st.orm.repository.EntityRepository;

import java.lang.reflect.RecordComponent;
import java.util.stream.Stream;

import static st.orm.spi.Providers.getORMReflection;

/**
 * Optional marker interface for record-based entities.
 *
 * <p>This interface is only required when ORM mapping is used in combination with {@code EntityRepository}.
 *
 * @param <ID> the type of the entity's primary key.
 * @see EntityRepository
 */
public interface KEntity<ID> extends Entity<ID> {

    @SuppressWarnings("unchecked")
    private Stream<RecordComponent> getPkComponents(@Nonnull Class<? extends Record> componentType) {
        var reflection = getORMReflection();
        return Stream.of(componentType.getRecordComponents())
                .flatMap(c -> reflection.isAnnotationPresent(c, PK.class)
                        ? Stream.of(c)
                        : reflection.isAnnotationPresent(c, Inline.class) && c.getType().isRecord()
                        ? getPkComponents((Class<? extends Record>) c.getType())
                        : Stream.empty());
    }

    /**
     * Returns the primary key of the entity.
     *
     * The primary key can be any type, such as a {@code Integer} or {@code Long}, but compound keys are also supported.
     *
     * @return the primary key of the entity.
     */
    @SuppressWarnings("unchecked")
    default ID id() {
        var pkComponent = getPkComponents((Class<? extends Record>) getClass()).findFirst().orElseThrow(() -> new IllegalStateException(STR."No primary key found for \{getClass().getSimpleName()}."));
        try {
            return (ID) getORMReflection().invokeComponent(pkComponent, this);
        } catch (PersistenceException e) {
            throw e;
        } catch (Throwable t) {
            throw new PersistenceException(t);
        }
    }
}