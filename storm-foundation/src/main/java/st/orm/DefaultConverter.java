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
package st.orm;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a {@link st.orm.Converter Converter} as eligible for automatic application when its entity type matches a
 * type and no explicit {@link st.orm.Convert @Convert} annotation is present.
 *
 * <p>When this annotation is present on a converter class, Storm may apply it automatically during mapping resolution.
 * If multiple auto-apply converters match the same component type, resolution will fail with a clear error unless
 * an explicit converter is specified.</p>
 *
 * @since 1.7
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface DefaultConverter {
}
