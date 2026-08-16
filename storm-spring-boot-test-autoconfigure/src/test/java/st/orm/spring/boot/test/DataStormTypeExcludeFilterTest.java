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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Service;
import st.orm.spring.boot.test.domain.UnrelatedService;
import st.orm.spring.boot.test.domain.Visit;

/**
 * Verifies the slice's type exclusion: regular components stay out unless an include filter names them, an exclude
 * filter wins over an include filter, {@code useDefaultFilters = false} lets everything in, every filter type the
 * annotation offers is honored, and two filters built from equal annotations are equal, which is what lets test
 * classes with the same slice configuration share a context.
 */
class DataStormTypeExcludeFilterTest {

    private final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

    @DataStormTest
    static class Defaults {
    }

    @DataStormTest
    static class SameDefaults {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UnrelatedService.class))
    static class IncludesByType {
    }

    @DataStormTest(
            includeFilters = @Filter(type = FilterType.ANNOTATION, classes = Service.class),
            excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UnrelatedService.class))
    static class ExcludeWinsOverInclude {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.REGEX, pattern = ".*\\.domain\\.Unrelated.*"))
    static class IncludesByRegex {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.ASPECTJ, pattern = "st.orm.spring.boot.test.domain.*"))
    static class IncludesByAspectJ {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.CUSTOM, classes = ServicesOnly.class))
    static class IncludesByCustomFilter {
    }

    @DataStormTest(useDefaultFilters = false)
    static class WithoutDefaultFilters {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.REGEX, classes = UnrelatedService.class))
    static class RegexWithClasses {
    }

    @DataStormTest(includeFilters = @Filter(type = FilterType.ANNOTATION, pattern = ".*"))
    static class AnnotationWithPattern {
    }

    /** A custom filter, instantiated by the slice from the annotation, that admits {@code @Service} classes. */
    public static class ServicesOnly implements TypeFilter {
        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
            return metadataReader.getAnnotationMetadata().hasAnnotation(Service.class.getName());
        }
    }

    private static DataStormTypeExcludeFilter filterOf(Class<?> annotated) {
        return new DataStormTypeExcludeFilter(annotated.getAnnotation(DataStormTest.class));
    }

    private boolean excludes(DataStormTypeExcludeFilter filter, Class<?> candidate) throws IOException {
        return filter.match(metadataReaderFactory.getMetadataReader(candidate.getName()), metadataReaderFactory);
    }

    @Test
    void defaultFilteringKeepsEveryScannedComponentOut() throws IOException {
        var filter = filterOf(Defaults.class);
        assertThat(excludes(filter, UnrelatedService.class)).isTrue();
        assertThat(excludes(filter, Visit.class)).isTrue();
    }

    @Test
    void anIncludeFilterAdmitsTheComponentItNames() throws IOException {
        var filter = filterOf(IncludesByType.class);
        assertThat(excludes(filter, UnrelatedService.class)).isFalse();
        assertThat(excludes(filter, Visit.class)).as("components the include filter does not name stay out").isTrue();
    }

    @Test
    void anExcludeFilterWinsOverAnIncludeFilter() throws IOException {
        assertThat(excludes(filterOf(ExcludeWinsOverInclude.class), UnrelatedService.class)).isTrue();
    }

    @Test
    void patternAndCustomFilterTypesAreHonored() throws IOException {
        assertThat(excludes(filterOf(IncludesByRegex.class), UnrelatedService.class)).isFalse();
        assertThat(excludes(filterOf(IncludesByAspectJ.class), UnrelatedService.class)).isFalse();
        assertThat(excludes(filterOf(IncludesByCustomFilter.class), UnrelatedService.class)).isFalse();
        assertThat(excludes(filterOf(IncludesByCustomFilter.class), Visit.class)).isTrue();
    }

    @Test
    void withoutDefaultFiltersEverythingIsAdmitted() throws IOException {
        var filter = filterOf(WithoutDefaultFilters.class);
        assertThat(excludes(filter, UnrelatedService.class)).isFalse();
        assertThat(excludes(filter, Visit.class)).isFalse();
    }

    @Test
    void aFilterTypeUsedWithTheWrongAttributeFailsAtConstruction() {
        assertThatThrownBy(() -> filterOf(RegexWithClasses.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGEX")
                .hasMessageContaining("pattern");
        assertThatThrownBy(() -> filterOf(AnnotationWithPattern.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANNOTATION")
                .hasMessageContaining("patterns");
    }

    @Test
    void filtersBuiltFromEqualAnnotationsAreEqual() {
        // Two test classes with the same slice configuration produce equal filters, so the context customizers
        // that carry them compare equal and the classes share one Spring context.
        assertThat(filterOf(Defaults.class)).isEqualTo(filterOf(SameDefaults.class));
        assertThat(filterOf(Defaults.class).hashCode()).isEqualTo(filterOf(SameDefaults.class).hashCode());
        assertThat(filterOf(Defaults.class)).isNotEqualTo(filterOf(IncludesByType.class));
        assertThat(filterOf(Defaults.class)).isNotEqualTo(filterOf(WithoutDefaultFilters.class));
        assertThat(filterOf(Defaults.class)).isNotEqualTo("not a filter");
    }
}
