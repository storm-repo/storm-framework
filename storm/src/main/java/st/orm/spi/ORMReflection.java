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
package st.orm.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Optional;

/**
 * Provides pluggable reflection support for the ORM to support different JVM languages, such as Java and Kotlin.
 */
public interface ORMReflection {

    /**
     * Returns the canonical constructor for the specified record type.
     *
     * <p>The canonical constructor is the base constructor that is used to create new instances of the record type.</p>
     *
     * @param type the record type.
     * @return the record components of the specified record type.
     */
    Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> type);

    /**
     * Returns the primary key type for the specified record type, if available.
     *
     * @param type the record type.
     * @return the primary key type for the specified record type, or an empty optional if no primary key type is
     * available.
     */
    Optional<Class<?>> findPKType(@Nonnull Class<? extends Record> type);

    /**
     * Checks if the specified record component has the specified annotation.
     *
     * @param component the record component to check for the specified annotation.
     * @param annotationType the annotation type to check for.
     * @return true if the specified record component has the specified annotation, false otherwise.
     */
    boolean isAnnotationPresent(@Nonnull RecordComponent component, @Nonnull Class<? extends Annotation> annotationType);

    /**
     * Checks if the specified type has the specified annotation.
     *
     * @param type the type to check for the specified annotation.
     * @param annotationType the annotation type to check for.
     * @return true if the specified type has the specified annotation, false otherwise.
     */
    boolean isAnnotationPresent(@Nonnull Class<?> type, @Nonnull Class<? extends Annotation> annotationType);

    /**
     * Returns the annotation of the specified type for the specified record component, if present.
     *
     * @param component the record component to get the annotation from.
     * @param annotationType the annotation type to get.
     * @return the annotation of the specified type for the specified record component, or {@code null} if not present.
     * @param <A> the annotation type.
     */
    <A extends Annotation> A getAnnotation(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType);

    /**
     * Returns the annotation of the specified type for the specified type, if present.
     *
     * @param type the type to get the annotation from.
     * @param annotationType the annotation type to get.
     * @return the annotation of the specified type for the specified type, or {@code null} if not present.
     * @param <A> the annotation type.
     */
    <A extends Annotation> A getAnnotation(@Nonnull Class<?> type, @Nonnull Class<A> annotationType);

    boolean isSupportedType(@Nonnull Object o);

    Class<?> getType(@Nonnull Object o);

    Class<? extends Record> getRecordType(@Nonnull Object o);

    boolean isDefaultValue(@Nullable Object o);

    /**
     * Returns true if the specified component has a non-null annotation, false otherwise.
     *
     * @param component the component to check for a non-null annotation.
     * @return true if the specified component has a non-null annotation, false otherwise.
     */
    boolean isNonnull(@Nonnull RecordComponent component);

    Object invokeComponent(@Nonnull RecordComponent component, @Nonnull Object record) throws Throwable;

    boolean isDefaultMethod(@Nonnull Method method);

    Object execute(@Nonnull Object proxy, @Nonnull Method method, Object... args) throws Throwable;
}
