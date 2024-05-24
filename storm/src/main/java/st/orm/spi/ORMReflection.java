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
package st.orm.spi;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Optional;

public interface ORMReflection {

    Optional<Constructor<?>> findCanonicalConstructor(@Nonnull Class<? extends Record> recordType);

    Optional<Class<?>> findPKType(@Nonnull Class<? extends Record> type);

    boolean isAnnotationPresent(@Nonnull RecordComponent component, @Nonnull Class<? extends Annotation> annotationType);

    boolean isAnnotationPresent(@Nonnull Class<?> type, @Nonnull Class<? extends Annotation> annotationType);

    <A extends Annotation> A getAnnotation(@Nonnull RecordComponent component, @Nonnull Class<A> annotationType);

    <A extends Annotation> A getAnnotation(@Nonnull Class<?> type, @Nonnull Class<A> annotationType);

    boolean isSupportedType(@Nonnull Object o);

    Class<?> getType(@Nonnull Object o);

    Class<? extends Record> getRecordType(@Nonnull Object o);

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
