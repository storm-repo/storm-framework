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
package st.orm.core.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.core.template.TemplateString;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for processing string templates.
 *
 * @since 1.2
 */
public final class StringTemplates {

    private StringTemplates() {
    }

    /**
     * Flattens a string template by merging nested templates into a single template.
     *
     * @param template the template to flatten.
     * @return the flattened template.
     */
    public static TemplateString flatten(@Nonnull TemplateString template) {
        FlattenResult result = flattenTemplate(template);
        return TemplateString.of(result.fragments, result.values);
    }

    private record FlattenResult(List<String> fragments, List<Object> values) {}

    private static FlattenResult flattenTemplate(@Nonnull TemplateString template) {
        List<String> tmplFragments = template.fragments();
        List<Object> tmplValues = template.values();
        if (tmplValues.stream().noneMatch(v -> v instanceof TemplateString)) {
            return new FlattenResult(tmplFragments, tmplValues);
        }
        int substitutionCount = tmplValues.size();
        // Pre-size lists: fragments will have one more element than substitution values.
        List<String> fragments = new ArrayList<>(substitutionCount + 1);
        List<Object> values = new ArrayList<>(substitutionCount);
        fragments.add(tmplFragments.getFirst());
        for (int i = 0; i < substitutionCount; i++) {
            Object sub = tmplValues.get(i);
            String nextFragment = tmplFragments.get(i + 1);
            if (sub instanceof TemplateString nestedTemplate) {
                FlattenResult nested = flattenTemplate(nestedTemplate);
                // Merge the nested template's first fragment with the current last fragment.
                int lastIndex = fragments.size() - 1;
                fragments.set(lastIndex, fragments.get(lastIndex) + nested.fragments.getFirst());
                // Append each nested substitution and its following literal fragment.
                for (int j = 0, nestedCount = nested.values.size(); j < nestedCount; j++) {
                    values.add(nested.values.get(j));
                    fragments.add(nested.fragments.get(j + 1));
                }
                // Append the outer literal fragment.
                lastIndex = fragments.size() - 1;
                fragments.set(lastIndex, fragments.get(lastIndex) + nextFragment);
            } else {
                // For non-nested substitutions, simply add the value and literal.
                values.add(sub);
                fragments.add(nextFragment);
            }
        }
        return new FlattenResult(fragments, values);
    }
}