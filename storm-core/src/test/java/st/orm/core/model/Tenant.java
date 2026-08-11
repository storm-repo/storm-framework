/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package st.orm.core.model;

import lombok.Builder;
import st.orm.Entity;
import st.orm.FK;
import st.orm.PK;

/**
 * Test entity that creates a diamond join graph by reaching {@link City} through two independent
 * paths: directly via {@link #city()} and indirectly via {@link Owner#address()}.{@code city()}.
 *
 * <p>Used to exercise alias path resolution when the {@code SELECT} target's expanded column tree
 * references a table that is reachable through multiple paths from the {@code FROM} table.</p>
 */
@Builder(toBuilder = true)
public record Tenant(
        @PK Integer id,
        String name,
        @FK Owner owner,
        @FK City city
) implements Entity<Integer> {
}
