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
package st.orm;

import static st.orm.EntityHelper.getId;

/**
 * Optional marker interface for record-based entities.
 *
 * <p>This interface is used to support the {@code EntityRepository}, which provides CRUD logic out-of-the-box.</p>
 *
 * @param <ID> the type of the entity's primary key.
 */
public interface Entity<ID> {

    /**
     * Returns the primary key of the entity.
     *
     * <p>The primary key can be any type, such as a {@code Integer} or {@code Long}, but records representing compound
     * keys are also supported.</p>
     *
     * @return the primary key of the entity.
     */
    default ID id() {
        return getId(this);
    }
}