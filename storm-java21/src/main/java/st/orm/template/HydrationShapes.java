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
package st.orm.template;

/**
 * How summary rows render the declared hydration shape of their statement's type.
 *
 * @see SqlScope#hydrationShapes(HydrationShapes)
 * @since 1.13
 */
public enum HydrationShapes {

    /** No shape renders. The default. */
    OFF,

    /**
     * A row whose type hydrates beyond its own table ends with the numeric shape, {@code j2 c12 d3}: joins,
     * columns, and graph depth. A flat type shows none.
     */
    SHORT,

    /** Every mapped row ends with the full shape, {@code joins=2 columns=12 graph=Pet(Owner(City))}. */
    FULL
}
