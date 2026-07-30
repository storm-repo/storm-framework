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
package st.orm.core.template.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import st.orm.core.template.impl.Elements.Fetch;

/**
 * The fetch element canonicalizes its plan on construction, so equivalent plans share a compiled statement.
 */
public class FetchElementTest {

    @Test
    public void pathsArePrefixClosed() {
        assertEquals(Set.of("city", "city.country"), new Fetch(Set.of("city.country")).paths());
    }

    @Test
    public void equivalentPlansCompareEqual() {
        assertEquals(new Fetch(Set.of("city", "city.country")), new Fetch(Set.of("city.country")));
        assertEquals(new Fetch(List.of("city.country", "city")), new Fetch(List.of("city.country")));
    }

    @Test
    public void emptyPlanIsEmpty() {
        assertTrue(new Fetch(Set.of()).paths().isEmpty());
    }
}
