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
import st.orm.template.Metamodel;

/**
 * Implementation that is used by the generated models.
 *
 * @param <T> the primary table type.
 * @param <E> the record component type of the designated element.
 * @since 1.2
 */
@SuppressWarnings("ClassCanBeRecord")
public class MetamodelImpl<T extends Record, E> implements Metamodel<T, E> {

    private final Class<? extends Record> table;
    private final String path;
    private final String component;
    private final Class<E> componentType;

    public MetamodelImpl(@Nonnull Class<? extends Record> table,
                         @Nonnull String path,
                         @Nonnull String component,
                         @Nonnull Class<E> componentType) {
        this.path = path;
        this.table = table;
        this.component = component;
        this.componentType = componentType;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public Class<? extends Record> table() {
        return table;
    }

    @Override
    public String component() {
        return component;
    }

    @Override
    public Class<E> componentType() {
        return componentType;
    }

    @Override
    public String toString() {
        return STR."Metamodel{table=\{table}, path='\{path}', component='\{component}', componentType=\{componentType}}";
    }
}
