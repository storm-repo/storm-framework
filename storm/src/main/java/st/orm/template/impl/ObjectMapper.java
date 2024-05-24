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
import st.orm.template.SqlTemplateException;

/**
 * Mapper for creating instances of a specific type.
 *
 * @param <T> The type of the instance to create.
 */
public interface ObjectMapper<T> {

    /**
     * Returns the parameter types of the constructor used to create instances.
     *
     * @return the parameter types of the constructor used to create instances.
     * @throws SqlTemplateException if the parameter types could not be determined.
     */
    Class<?>[] getParameterTypes() throws SqlTemplateException;

    /**
     * Creates a new instance of the type.
     *
     * @param args the arguments to pass to the constructor.
     * @return a new instance of the type.
     * @throws SqlTemplateException if the instance could not be created.
     */
    T newInstance(@Nonnull Object[] args) throws SqlTemplateException;
}
