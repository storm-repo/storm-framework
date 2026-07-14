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
package st.orm.core.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;

/**
 * Resolves the nullability of record components and constructor parameters under Storm's null-marked-by-default
 * contract.
 *
 * <p>Storm aligns its nullability semantics with JSpecify, one deliberate step further: <em>models are null-marked
 * by default</em>. For Kotlin classes the language type is the contract, resolved from Kotlin metadata by the
 * Kotlin reflection provider. For Java, a type use is non-null unless declared otherwise:</p>
 * <ul>
 *   <li>An explicit {@code @Nullable} — JSpecify's type-use annotation, {@code jakarta.annotation.Nullable}, or
 *       {@code javax.annotation.Nullable} — makes the type use nullable regardless of scope.</li>
 *   <li>An explicit {@code @NonNull} / {@code @Nonnull} makes it non-null regardless of scope.</li>
 *   <li>Otherwise the nearest {@code @NullUnmarked} / {@code @NullMarked} marker decides, walking the constructor
 *       (if any), the declaring class and its enclosing classes, the package, and the module. Without a marker,
 *       the type use is <em>non-null</em>: unmarked code is interpreted as null-marked.</li>
 * </ul>
 *
 * <p>JSpecify treats unmarked code as unspecified and asks static tools to be lenient. Storm is a runtime
 * boundary: the lenient interpretation lets {@code NULL} flow silently into fields the developer assumed
 * non-null, surfacing far from the cause. Interpreting unspecified as non-null fails fast with a descriptive
 * error naming the component and the fix, and makes Java models behave exactly like Kotlin models, where
 * nullable is the marked case. {@code @NullUnmarked} is the opt-out for models that want the lenient
 * behavior.</p>
 *
 * <p>The JSpecify annotations are resolved reflectively: they are not a runtime dependency of the framework.
 * Resolution only runs when record mapping metadata is built, never on the row mapping hot path.</p>
 */
public final class Nullability {

    private static final Class<? extends Annotation> JSPECIFY_NON_NULL = load("org.jspecify.annotations.NonNull");
    private static final Class<? extends Annotation> JSPECIFY_NULLABLE = load("org.jspecify.annotations.Nullable");
    private static final Class<? extends Annotation> NULL_MARKED = load("org.jspecify.annotations.NullMarked");
    private static final Class<? extends Annotation> NULL_UNMARKED = load("org.jspecify.annotations.NullUnmarked");
    private static final Class<? extends Annotation> JAVAX_NONNULL = load("javax.annotation.Nonnull");
    private static final Class<? extends Annotation> JAVAX_NULLABLE = load("javax.annotation.Nullable");
    private static final Class<? extends Annotation> JAKARTA_NONNULL = load("jakarta.annotation.Nonnull");
    private static final Class<? extends Annotation> JAKARTA_NULLABLE = load("jakarta.annotation.Nullable");
    private static final Class<? extends Annotation> KOTLIN_METADATA = load("kotlin.Metadata");

    @Nullable
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> load(@Nonnull String name) {
        try {
            return (Class<? extends Annotation>) Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private Nullability() {
    }

    /**
     * Returns whether the given type use is non-null under the null-marked-by-default contract.
     *
     * @param declaration the record component or parameter declaration, carrying declaration annotations.
     * @param annotatedType the annotated type of the declaration, carrying type-use annotations.
     * @param executable the constructor the type use belongs to, or null for record components.
     * @param declaringClass the class declaring the record component or constructor.
     * @return {@code true} if the type use is non-null.
     */
    public static boolean isNonNull(@Nonnull AnnotatedElement declaration,
                                    @Nonnull AnnotatedType annotatedType,
                                    @Nullable Executable executable,
                                    @Nonnull Class<?> declaringClass) {
        // Explicit annotations always win, nullable before non-null.
        if (isPresent(declaration, JAVAX_NULLABLE) || isPresent(declaration, JAKARTA_NULLABLE)
                || isPresent(annotatedType, JSPECIFY_NULLABLE)) {
            return false;
        }
        if (isPresent(declaration, JAVAX_NONNULL) || isPresent(declaration, JAKARTA_NONNULL)
                || isPresent(annotatedType, JSPECIFY_NON_NULL)) {
            return true;
        }
        if (isPresent(declaringClass, KOTLIN_METADATA)) {
            // Kotlin nullness comes from the language, resolved by the Kotlin reflection provider.
            return false;
        }
        // The nearest scope marker decides; without one, the model is null-marked.
        return !isNullUnmarked(executable, declaringClass);
    }

    /**
     * Resolves the nearest {@code @NullUnmarked} / {@code @NullMarked} marker for the given declaration site.
     */
    private static boolean isNullUnmarked(@Nullable Executable executable, @Nonnull Class<?> declaringClass) {
        if (NULL_UNMARKED == null) {
            return false; // JSpecify is not on the class path; there is no way to opt out of the default.
        }
        if (executable != null) {
            Boolean unmarked = marker(executable);
            if (unmarked != null) {
                return unmarked;
            }
        }
        for (Class<?> enclosing = declaringClass; enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            Boolean unmarked = marker(enclosing);
            if (unmarked != null) {
                return unmarked;
            }
        }
        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage != null) {
            Boolean unmarked = marker(declaringPackage);
            if (unmarked != null) {
                return unmarked;
            }
        }
        Boolean unmarked = marker(declaringClass.getModule());
        return unmarked != null && unmarked;
    }

    @Nullable
    private static Boolean marker(@Nonnull AnnotatedElement element) {
        if (element.isAnnotationPresent(NULL_UNMARKED)) {
            return Boolean.TRUE;
        }
        if (NULL_MARKED != null && element.isAnnotationPresent(NULL_MARKED)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean isPresent(@Nonnull AnnotatedElement element,
                                     @Nullable Class<? extends Annotation> annotation) {
        return annotation != null && element.isAnnotationPresent(annotation);
    }
}
