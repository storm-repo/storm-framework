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

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The references a query resolves as part of the statement, named by field path relative to the record the query
 * materializes.
 *
 * <p>A plan is prefix-closed: naming {@code city.country} also names {@code city}, because the country reference is
 * held by the city record, which the query must materialize to hold it. Closing the plan on construction lets every
 * consumer test membership with an exact match rather than a prefix scan.</p>
 *
 * <p>The plan is a value: it participates in the cache keys of the model and of the compiled row mapper, which both
 * depend on which references a statement resolves.</p>
 *
 * @param paths the prefix-closed field paths, relative to the record the plan applies to.
 * @since 1.13
 */
record FetchPlan(Set<String> paths) {

    /** The plan of a query that resolves no reference: every reference is selected as its foreign key column. */
    static final FetchPlan NONE = new FetchPlan(Set.of());

    FetchPlan {
        paths = Set.copyOf(paths);
    }

    /**
     * Returns the plan for the given paths, closed over the prefixes of each path.
     *
     * @param paths the field paths to resolve, each relative to the record the plan applies to.
     * @return the prefix-closed plan.
     */
    static FetchPlan of(Collection<String> paths) {
        requireNonNull(paths, "paths");
        if (paths.isEmpty()) {
            return NONE;
        }
        Set<String> closed = new LinkedHashSet<>();
        for (String path : paths) {
            for (int dot = path.indexOf('.'); dot >= 0; dot = path.indexOf('.', dot + 1)) {
                closed.add(path.substring(0, dot));
            }
            closed.add(path);
        }
        return new FetchPlan(closed);
    }

    /**
     * Returns the paths in a stable order, for reporting the plan back as part of the statement.
     */
    List<String> toList() {
        return paths.stream().sorted().toList();
    }

    boolean isEmpty() {
        return paths.isEmpty();
    }

    /**
     * Returns whether the field at the given name is resolved by this plan.
     *
     * @param field the field name, relative to the record the plan applies to.
     * @return {@code true} if the query resolves the reference held by that field.
     */
    boolean fetches(String field) {
        return paths.contains(field);
    }

    /**
     * Returns the plan that applies to the record held by the given field, with the field's own prefix removed.
     *
     * @param field the field name, relative to the record the plan applies to.
     * @return the plan for the nested record, empty when nothing below the field is resolved.
     */
    FetchPlan descend(String field) {
        if (paths.isEmpty()) {
            return NONE;
        }
        String prefix = field + ".";
        Set<String> nested = new LinkedHashSet<>();
        for (String path : paths) {
            if (path.startsWith(prefix)) {
                nested.add(path.substring(prefix.length()));
            }
        }
        return nested.isEmpty() ? NONE : new FetchPlan(nested);
    }
}
