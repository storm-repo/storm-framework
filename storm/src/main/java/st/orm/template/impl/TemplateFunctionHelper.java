/*
 * Copyright 2024 the original author or authors.
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
package st.orm.template.impl;

import jakarta.annotation.Nonnull;
import st.orm.template.TemplateFunction;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;

final class TemplateFunctionHelper {

    private static final String NULL_CHARACTER = "\0";

    static StringTemplate template(@Nonnull TemplateFunction function) {
        return template(function, false);
    }

    static StringTemplate template(@Nonnull TemplateFunction function, boolean newLine) {
        List<Object> values = new ArrayList<>();
        String str = STR."\{newLine ? "\n" : ""}\{function.interpolate(o -> {
            values.add(o);
            return NULL_CHARACTER;  // Use NULL_CHARACTER as a safe delimiter.
        })}";
        return StringTemplate.of(stream(str.split(NULL_CHARACTER, -1)).toList(), values);   // Use -1 to keep trailing empty strings.
    }
}
