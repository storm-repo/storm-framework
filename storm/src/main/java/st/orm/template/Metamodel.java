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
package st.orm.template;

/**
 * The metamodel is used to map database columns to the object model in a type-safe way.
 *
 * @param <T> the primary table type.
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
public interface Metamodel<T extends Record, E> {

    /**
     * Returns the record type of the designated element.
     *
     * @return the record type of the designated element.
     */
    Class<? extends Record> table();

    /**
     * Returns the record component type of the designated element.
     *
     * @return the record component type of the designated element.
     */
    Class<E> componentType();

    /**
     * Returns the path to the database table.
     *
     * @return path to the database table.
     */
    String path();

    /**
     * Returns the component path.
     *
     * @return component path.
     */
    String component();

    /**
     * Returns the component path.
     *
     * @return component path.
     */
    default String componentPath() {
        String path = path();
        return path.isEmpty() ? component() : STR."\{path}.\{component()}";
    }
}
