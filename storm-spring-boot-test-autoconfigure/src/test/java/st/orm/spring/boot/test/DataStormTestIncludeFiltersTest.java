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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import st.orm.spring.boot.test.domain.UnrelatedService;
import st.orm.spring.boot.test.domain.VisitRepository;

/**
 * Verifies that {@code includeFilters} pulls a regular component into the slice that the default filtering keeps
 * out, while the slice's own beans are unaffected.
 */
@DataStormTest(includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = UnrelatedService.class))
class DataStormTestIncludeFiltersTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void anIncludedComponentJoinsTheSlice() {
        assertThat(applicationContext.getBean(UnrelatedService.class)).isNotNull();
        assertThat(applicationContext.getBean(VisitRepository.class)).isNotNull();
    }
}
