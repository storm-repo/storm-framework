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
package st.orm.core.template;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import st.orm.core.template.impl.SegmentedLruCache;

/**
 * Parses raw template strings into fragment lists.
 *
 * <p>Fragment lists are a pure function of the raw template, and interpolated values travel separately as
 * {@code \0} placeholders, so the cache key space is bounded by the distinct template shapes of the application.
 * Cached lists are immutable and shared.</p>
 */
final class TemplateFragments {

    private static final SegmentedLruCache<String, List<String>> CACHE = new SegmentedLruCache<>(1024);

    private TemplateFragments() {
    }

    /**
     * Parses the given string into template fragments, splitting on unescaped NULs (\0)
     * and turning "\\0" into a real NUL within fragments.
     *
     * @param raw the raw string with '\0' delimiters and '\\0' escapes.
     * @return an immutable list of fragments between each NUL delimiter.
     */
    static List<String> parse(@Nonnull String raw) {
        var cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        List<String> fragments = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '0') {
                // Escaped null sequence.
                cur.append('\0');
                i++;
            } else if (c == '\0') {
                // Delimiter: end fragment.
                fragments.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fragments.add(cur.toString());
        var result = List.copyOf(fragments);
        CACHE.put(raw, result);
        return result;
    }
}
