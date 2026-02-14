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
package st.orm.mapping;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import st.orm.PersistenceException;

/**
 * Utility methods for working with annotations, including support for repeatable annotations.
 *
 * <p>This class provides enhanced annotation lookup that properly handles both direct annotations
 * and repeatable annotations (annotations marked with {@link Repeatable}). When multiple instances
 * of a repeatable annotation are present, they are automatically unwrapped from their container
 * annotation.</p>
 *
 * @since 1.7
 */
final class Annotations {

    private Annotations() {
    }

    /**
     * Checks whether an annotation of the specified type is present in the given list.
     *
     * <p>This method handles both direct annotations and repeatable annotations. For repeatable
     * annotations, it will find them whether they appear directly (single instance) or wrapped
     * in their container annotation (multiple instances).
     *
     * @param annotations the list of annotations to search
     * @param annotationType the type of annotation to look for
     * @return {@code true} if at least one annotation of the specified type is present
     */
    public static boolean isAnnotationPresent(@Nonnull List<Annotation> annotations,
                                              @Nonnull Class<? extends Annotation> annotationType) {
        return getAnnotation(annotations, annotationType) != null;
    }

    /**
     * Returns a single annotation of the specified type from the given list, or {@code null} if
     * not present or if multiple instances are found.
     *
     * <p>This method is useful when you expect exactly one instance of an annotation. If multiple
     * instances of a repeatable annotation are present, this method returns {@code null} to indicate
     * ambiguity. Use {@link #getAnnotations(List, Class)} to retrieve all instances.
     *
     * @param <A> the type of the annotation
     * @param annotations the list of annotations to search
     * @param annotationType the type of annotation to retrieve
     * @return the annotation if exactly one instance is present, otherwise {@code null}
     */
    @Nullable
    public static <A extends Annotation> A getAnnotation(@Nonnull List<Annotation> annotations,
                                                         @Nonnull Class<A> annotationType) {
        A[] all = getAnnotations(annotations, annotationType);
        return all.length != 1 ? null : all[0];
    }

    /**
     * Returns all annotations of the specified type from the given list.
     *
     * <p>This method properly handles repeatable annotations by:
     * <ul>
     *   <li>Finding direct instances of the annotation</li>
     *   <li>Unwrapping instances from container annotations (for repeatable annotations)</li>
     * </ul>
     *
     * <p>For example, if {@code @DbColumn} is repeatable with container {@code @DbColumns},
     * this method will find all {@code @DbColumn} instances whether they appear directly or
     * are wrapped in a {@code @DbColumns} container.
     *
     * @param <A> the type of the annotation
     * @param annotations the list of annotations to search
     * @param annotationType the type of annotation to retrieve
     * @return an array of all matching annotations, or an empty array if none are found
     */
    public static <A extends Annotation> A[] getAnnotations(@Nonnull List<Annotation> annotations,
                                                            @Nonnull Class<A> annotationType) {
        List<A> result = new ArrayList<>();
        // Direct annotations.
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == annotationType) {
                result.add(annotationType.cast(annotation));
            }
        }
        // Repeatable annotations via container.
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            Class<? extends Annotation> containerType = repeatable.value();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == containerType) {
                    extractFromContainer(annotation, containerType, annotationType, result);
                }
            }
        }
        @SuppressWarnings("unchecked")
        A[] array = (A[]) Array.newInstance(annotationType, result.size());
        return result.toArray(array);
    }

    /**
     * Extracts repeatable annotations from their container annotation.
     *
     * <p>This method uses reflection to invoke the {@code value()} method on the container
     * annotation, which by convention returns an array of the contained annotations.
     *
     * @param <A> the type of the contained annotation
     * @param container the container annotation instance
     * @param containerType the class of the container annotation
     * @param annotationType the class of the contained annotation type
     * @param into the list to add extracted annotations to
     * @throws PersistenceException if the container does not have a {@code value()} method or
     *         if reflection fails
     */
    private static <A extends Annotation> void extractFromContainer(@Nonnull Annotation container,
                                                                    @Nonnull Class<? extends Annotation> containerType,
                                                                    @Nonnull Class<A> annotationType,
                                                                    @Nonnull List<A> into) {
        try {
            Method valueMethod = containerType.getMethod("value");
            Object value = valueMethod.invoke(container);
            if (value == null || !value.getClass().isArray()) {
                return;
            }
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(value, i);
                if (annotationType.isInstance(element)) {
                    into.add(annotationType.cast(element));
                }
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new PersistenceException("Failed to extract repeatable annotations from container %s.".formatted(containerType.getName()), e);
        }
    }
}
