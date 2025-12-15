/*
 * Copyright 2024 - 2025 the original author or authors.
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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import st.orm.Data;
import st.orm.Entity;
import st.orm.PersistenceException;
import st.orm.mapping.RecordField;
import st.orm.mapping.RecordType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Provides pluggable reflection support for the ORM to support different JVM languages, such as Java and Kotlin.
 */
public interface ORMReflection {

    <ID, E extends Entity<ID>> ID getId(@Nonnull E entity);

    Optional<RecordType> findRecordType(@Nonnull Class<?> type);

    default RecordType getRecordType(@Nonnull Class<?> type) {
        return findRecordType(type)
                .orElseThrow(() -> new PersistenceException("Record type expected: %s.".formatted(type.getName())));
    }

    boolean isSupportedType(@Nonnull Object o);

    Class<?> getType(@Nonnull Object o);

    Class<? extends Data> getDataType(@Nonnull Object o);

    boolean isDefaultValue(@Nullable Object o);

    /**
     * Returns the permitted subclasses of the specified sealed class.
     *
     * @param sealedClass the sealed class to get the permitted subclasses for.
     * @return a list of permitted subclasses of the specified sealed class.
     */
    <T> List<Class<? extends T>> getPermittedSubclasses(@Nonnull Class<T> sealedClass);

    boolean isDefaultMethod(@Nonnull Method method);

    Object invoke(@Nonnull RecordType type, @Nonnull Object[] args);

    Object invoke(@Nonnull RecordField field, @Nonnull Object record);

    Object execute(@Nonnull Object proxy, @Nonnull Method method, @Nonnull Object... args) throws Throwable;
}
