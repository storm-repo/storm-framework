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
package st.orm.spring.boot.test;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AspectJTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ObjectUtils;

/**
 * {@link TypeExcludeFilter} for {@link DataStormTest @DataStormTest}: keeps regular application components
 * out of the slice, honoring the annotation's include and exclude filters.
 *
 * <p>Built directly on {@link TypeExcludeFilter}, whose location is stable across Spring Boot 3 and 4; the
 * filter is registered by {@link DataStormTestContextCustomizerFactory} rather than through
 * {@code @TypeExcludeFilters}, which moved between the releases.</p>
 *
 * @since 1.13
 */
public final class DataStormTypeExcludeFilter extends TypeExcludeFilter {

    private final DataStormTest annotation;
    private final List<TypeFilter> includeFilters;
    private final List<TypeFilter> excludeFilters;

    DataStormTypeExcludeFilter(@Nonnull DataStormTest annotation) {
        this.annotation = annotation;
        this.includeFilters = createFilters(annotation.includeFilters());
        this.excludeFilters = createFilters(annotation.excludeFilters());
    }

    @Override
    public boolean match(@Nonnull MetadataReader metadataReader,
                         @Nonnull MetadataReaderFactory metadataReaderFactory) throws IOException {
        for (TypeFilter exclude : excludeFilters) {
            if (exclude.match(metadataReader, metadataReaderFactory)) {
                return true;
            }
        }
        for (TypeFilter include : includeFilters) {
            if (include.match(metadataReader, metadataReaderFactory)) {
                return false;
            }
        }
        // With default filtering, every remaining scanned component stays out of the slice. Storm
        // repositories are unaffected: they are registered by the repository post-processor, not by
        // component scanning.
        return annotation.useDefaultFilters();
    }

    private static List<TypeFilter> createFilters(ComponentScan.Filter[] filters) {
        List<TypeFilter> typeFilters = new ArrayList<>();
        for (ComponentScan.Filter filter : filters) {
            for (Class<?> filterClass : filter.classes()) {
                typeFilters.add(createFilter(filter, filterClass));
            }
            for (String pattern : filter.pattern()) {
                typeFilters.add(switch (filter.type()) {
                    case REGEX -> new RegexPatternTypeFilter(java.util.regex.Pattern.compile(pattern));
                    case ASPECTJ -> new AspectJTypeFilter(pattern, DataStormTypeExcludeFilter.class.getClassLoader());
                    default -> throw new IllegalStateException(
                            "Filter type " + filter.type() + " does not support patterns.");
                });
            }
        }
        return typeFilters;
    }

    @SuppressWarnings("unchecked")
    private static TypeFilter createFilter(ComponentScan.Filter filter, Class<?> filterClass) {
        return switch (filter.type()) {
            case ANNOTATION -> new AnnotationTypeFilter((Class<? extends java.lang.annotation.Annotation>) filterClass);
            case ASSIGNABLE_TYPE -> new AssignableTypeFilter(filterClass);
            case CUSTOM -> (TypeFilter) org.springframework.beans.BeanUtils.instantiateClass(filterClass);
            default -> throw new IllegalStateException(
                    "Filter type " + filter.type() + " requires a pattern rather than classes.");
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DataStormTypeExcludeFilter filter
                && Objects.equals(annotationState(annotation), annotationState(filter.annotation));
    }

    @Override
    public int hashCode() {
        return annotationState(annotation).hashCode();
    }

    private static List<Object> annotationState(DataStormTest annotation) {
        return List.of(
                annotation.useDefaultFilters(),
                Arrays.stream(annotation.includeFilters()).map(AnnotationUtils::getAnnotationAttributes).toList(),
                Arrays.stream(annotation.excludeFilters()).map(AnnotationUtils::getAnnotationAttributes).toList(),
                ObjectUtils.nullSafeHashCode(annotation.properties()));
    }
}
